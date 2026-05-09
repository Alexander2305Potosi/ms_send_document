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
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
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

    private static final Logger LOGGER = Logger.getLogger(ProductRestGatewayAdapter.class.getName());

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
    public Flux<ProductDocumentHistory> getDocumentsByProduct(ProductHistory product) {
        return Flux.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            LOGGER.log(Level.INFO, "Fetching documents for product {0} from REST API, traceId: {1}",
                new Object[]{product.getProductId(), traceId});

            return webClient.get()
                .uri(properties.productDocumentsPath(), product.getProductId())
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToFlux(ProductDocumentResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(doc -> mapToProductDocument(product.getProductId(), doc))
                .doOnNext(doc -> LOGGER.log(Level.INFO, "Document retrieved: productId={0}, documentId={1}",
                    new Object[]{doc.getProductId(), doc.getDocumentId()}));
        });
    }

    @Override
    public Mono<ProductDocumentFile> getDocument(String productId, String documentId) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            LOGGER.log(Level.INFO, "Fetching document {0} for product {1} from REST API, traceId: {2}",
                new Object[]{documentId, productId, traceId});

            return webClient.get()
                .uri(properties.productDocumentsPath() + "/{documentId}", productId, documentId)
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToMono(ProductDocumentResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(response -> {
                    ProductDocumentHistory doc = mapToProductDocument(productId, response);
                    return ProductDocumentFile.builder()
                        .productId(productId)
                        .documentId(doc.getDocumentId())
                        .filename(doc.getFilename())
                        .content(doc.getContent())
                        .contentType(doc.getContentType())
                        .size(doc.getSize())
                        .isZip(doc.isZip())
                        .origin(doc.getOrigin())
                        .pais(doc.getPais())
                        .build();
                })
                .doOnNext(doc -> LOGGER.log(Level.INFO, "Document {0} retrieved for product {1}",
                    new Object[]{documentId, productId}));
        });
    }

    Flux<ProductDocumentHistory> mapToProductDocumentHistory(ProductResponse json) {
        if (json.getDocuments() == null || json.getDocuments().isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(json.getDocuments())
            .map(doc -> mapToProductDocument(json.getProductId(), doc));
    }

    ProductDocumentHistory mapToProductDocument(String productId, ProductDocumentResponse json) {
        String contentBase64 = json.getContent();
        String filename = json.getFilename();
        String documentId = json.getDocumentId();

        byte[] content;
        if (contentBase64 != null && !contentBase64.isBlank()) {
            try {
                content = Base64Utils.decodeSafe(contentBase64, filename, documentId);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to decode Base64 for document {0} ({1}): {2}",
                    new Object[]{documentId, filename, e.getMessage()});
                throw new ProcessingException(
                    "Base64 decode failed for document: " + documentId,
                    ProcessingResultCodes.INVALID_BASE64.name(), documentId);
            }
        } else {
            content = null;
        }

        long size = json.getSize() != null ? json.getSize() : (content != null ? content.length : 0);

        // Validate isZip based on filename extension
        boolean isZip = filename != null && filename.toLowerCase().endsWith(".zip");

        return ProductDocumentHistory.builder()
            .productId(productId)
            .documentId(documentId)
            .name(filename)
            .filename(filename)
            .contentType(json.getContentType())
            .size(size)
            .isZip(isZip)
            .origin(json.getOrigin())
            .pais(json.getPais())
            .content(content)
            .build();
    }
}
