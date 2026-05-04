package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.util.Base64Utils;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductResponse;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class ProductRestGatewayAdapter implements ProductRestGateway {

    private static final Logger log = Logger.getLogger(ProductRestGatewayAdapter.class.getName());

    private final WebClient webClient;
    private final DocumentRestProperties properties;

    public ProductRestGatewayAdapter(WebClient.Builder webClientBuilder,
                                     DocumentRestProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    public Flux<ProductDocumentHistory> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            log.log(Level.INFO, "Fetching all products from REST API, traceId: {0}", new Object[]{traceId});

            return webClient.get()
                .uri(properties.productsPath())
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToFlux(ProductResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .flatMap(this::mapToProductDocumentHistory)
                .doOnNext(doc -> log.log(Level.INFO, "Product document retrieved: productId={0}, documentId={1}",
                    new Object[]{doc.productId(), doc.documentId()}));
        });
    }

    @Override
    public Flux<ProductDocumentHistory> getDocumentsByProduct(ProductHistory product) {
        return Flux.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            log.log(Level.INFO, "Fetching documents for product {0} from REST API, traceId: {1}",
                new Object[]{product.productId(), traceId});

            String path = properties.productDocumentsPath().replace("{productId}", product.productId());

            return webClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToFlux(ProductDocumentResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .map(doc -> mapToProductDocument(product.productId(), doc))
                .doOnNext(doc -> log.log(Level.INFO, "Document retrieved: productId={0}, documentId={1}",
                    new Object[]{doc.productId(), doc.documentId()}));
        });
    }

    @Override
    public Mono<ProductDocumentFile> getDocument(String productId, String documentId) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            log.log(Level.INFO, "Fetching document {0} for product {1} from REST API, traceId: {2}",
                new Object[]{documentId, productId, traceId});

            String path = properties.productDocumentsPath().replace("{productId}", productId);

            return webClient.get()
                .uri(path + "/{documentId}", documentId)
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToMono(ProductDocumentResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .map(response -> {
                    ProductDocumentHistory doc = mapToProductDocument(productId, response);
                    return ProductDocumentFile.builder()
                        .productId(productId)
                        .documentId(doc.documentId())
                        .filename(doc.filename())
                        .content(doc.content())
                        .contentType(doc.contentType())
                        .size(doc.size())
                        .isZip(doc.isZip())
                        .origin(doc.origin())
                        .pais(doc.pais())
                        .build();
                })
                .doOnNext(doc -> log.log(Level.INFO, "Document {0} retrieved for product {1}",
                    new Object[]{documentId, productId}));
        });
    }

    private Flux<ProductDocumentHistory> mapToProductDocumentHistory(ProductResponse json) {
        if (json.documents() == null || json.documents().isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(json.documents())
            .map(doc -> mapToProductDocument(json.productId(), doc));
    }

    private ProductDocumentHistory mapToProductDocument(String productId, ProductDocumentResponse json) {
        String contentBase64 = json.content();
        String filename = json.filename();
        String documentId = json.documentId();

        byte[] content;
        if (contentBase64 != null && !contentBase64.isBlank()) {
            try {
                content = Base64Utils.decodeSafe(contentBase64, filename, documentId);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to decode Base64 for document {0} ({1}): {2}",
                    new Object[]{documentId, filename, e.getMessage()});
                throw new ProcessingException(
                    "Base64 decode failed for document: " + documentId,
                    com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64, documentId);
            }
        } else {
            content = null;
        }

        long size = json.size() != null ? json.size() : (content != null ? content.length : 0);

        return ProductDocumentHistory.builder()
            .productId(productId)
            .documentId(documentId)
            .name(filename)
            .filename(filename)
            .contentType(json.contentType())
            .size(size)
            .isZip(isZip(filename))
            .origin(json.origin())
            .pais(json.pais())
            .content(content)
            .build();
    }

    private boolean isZip(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".zip");
    }
}