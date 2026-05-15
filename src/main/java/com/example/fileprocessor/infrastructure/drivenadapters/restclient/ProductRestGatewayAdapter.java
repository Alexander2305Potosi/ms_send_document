package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.util.Base64Utils;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
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
    public Flux<Document> getDocumentsByProduct(ProductMaestro product) {
        return Flux.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown-trace");
            LOGGER.log(Level.INFO, "Fetching documents for product {0} from REST API, traceId: {1}",
                    new Object[] { product.getProductId(), traceId });

            return webClient.get()
                    .uri(properties.productDocumentsPath(), product.getProductId())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(ApiConstants.HEADER_TRACE_ID, traceId)
                    .retrieve()
                    .bodyToFlux(ProductDocumentResponse.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .map(doc -> mapToDocument(product.getProductId(), doc))
                    .doOnNext(doc -> LOGGER.log(Level.INFO, "Document retrieved: productId={0}, documentId={1}",
                            new Object[] { doc.getProductId(), doc.getDocumentId() }));
        });
    }

    @Override
    public Mono<ProductDocumentFile> getDocument(String productId, String documentId) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown-trace");
            LOGGER.log(Level.INFO, "Fetching document {0} for product {1} from REST API, traceId: {2}",
                    new Object[] { documentId, productId, traceId });

            return webClient.get()
                    .uri(properties.productDocumentsPath() + "/{documentId}", productId, documentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(ApiConstants.HEADER_TRACE_ID, traceId)
                    .retrieve()
                    .bodyToMono(ProductDocumentResponse.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .map(response -> mapToProductDocumentFile(productId, response))
                    .doOnNext(doc -> LOGGER.log(Level.INFO, "Document {0} retrieved for product {1}",
                            new Object[] { documentId, productId }));
        });
    }

    private ProductDocumentFile mapToProductDocumentFile(String productId, ProductDocumentResponse json) {
        byte[] content = decodeBase64(json);
        long size = json.getSize() != null ? json.getSize() : (content != null ? content.length : 0);

        return ProductDocumentFile.builder()
                .productId(productId)
                .documentId(json.getDocumentId())
                .filename(json.getFilename())
                .content(content)
                .contentType(json.getContentType())
                .size(size)
                .isZip(json.isZip() || (json.getFilename() != null && json.getFilename().toLowerCase().endsWith(".zip")))
                .origin(json.getOrigin())
                .pais(json.getPais())
                .build();
    }

    private Document mapToDocument(String productId, ProductDocumentResponse json) {
        return Document.builder()
                .productId(productId)
                .documentId(json.getDocumentId())
                .name(json.getFilename())
                .isZip(json.isZip() || (json.getFilename() != null && json.getFilename().toLowerCase().endsWith(".zip")))
                .build();
    }

    private byte[] decodeBase64(ProductDocumentResponse json) {
        String contentBase64 = json.getContent();
        if (contentBase64 == null || contentBase64.isBlank())
            return null;

        try {
            return Base64Utils.decodeSafe(contentBase64, json.getFilename(), json.getDocumentId());
        } catch (Exception e) {
            throw new ProcessingException(
                    "Base64 decode failed for document: " + json.getDocumentId(),
                    ProcessingResultCodes.INVALID_BASE64.name(), json.getDocumentId());
        }
    }
}
