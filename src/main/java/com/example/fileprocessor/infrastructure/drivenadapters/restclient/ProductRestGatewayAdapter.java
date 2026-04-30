package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.util.Base64Utils;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductResponse;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

@Component
public class ProductRestGatewayAdapter implements ProductRestGateway {

    private static final Logger log = LoggerFactory.getLogger(ProductRestGatewayAdapter.class);

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
    public Flux<ProductInfo> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            log.info("Fetching all products from REST API, traceId: {}", traceId);

            return webClient.get()
                .uri(properties.productsPath())
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToFlux(ProductResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .map(this::mapToProductInfo)
                .doOnNext(product -> log.info("Product retrieved: {}", product.getProductId()));
        });
    }

    @Override
    public Mono<ProductDocumentInfo> getDocument(String productId, String documentId) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            log.info("Fetching document {} for product {} from REST API, traceId: {}", documentId, productId, traceId);

            String path = properties.productDocumentsPath().replace("{productId}", productId);

            return webClient.get()
                .uri(path + "/{documentId}", documentId)
                .accept(MediaType.APPLICATION_JSON)
                .header(ApiConstants.HEADER_TRACE_ID, traceId)
                .retrieve()
                .bodyToMono(ProductDocumentResponse.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .map(this::mapToProductDocumentInfo)
                .doOnNext(doc -> log.info("Document {} retrieved for product {}", documentId, productId));
        });
    }

    private ProductInfo mapToProductInfo(ProductResponse json) {
        List<ProductDocumentInfo> documents = json.documents() != null
            ? json.documents().stream()
                .map(this::mapToProductDocumentInfo)
                .toList()
            : List.of();

        return ProductInfo.builder()
            .productId(json.productId())
            .name(json.name())
            .documents(documents)
            .build();
    }

    private ProductDocumentInfo mapToProductDocumentInfo(ProductDocumentResponse json) {
        String contentBase64 = json.content();
        String filename = json.filename();
        String documentId = json.documentId();

        byte[] content;
        if (contentBase64 != null && !contentBase64.isBlank()) {
            try {
                content = Base64Utils.decodeSafe(contentBase64, filename, documentId);
            } catch (Exception e) {
                log.error("Failed to decode Base64 for document {} ({}): {}",
                    documentId, filename, e.getMessage());
                throw new ProcessingException(
                    "Base64 decode failed for document: " + documentId,
                    com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64, documentId);
            }
        } else {
            content = null;
        }

        long size = json.size() != null ? json.size() : (content != null ? content.length : 0);
        boolean isZip = Boolean.TRUE.equals(json.isZip());

        return new ProductDocumentInfo(
            documentId,
            filename,
            content,
            json.contentType(),
            size,
            isZip,
            json.origin()
        );
    }
}
