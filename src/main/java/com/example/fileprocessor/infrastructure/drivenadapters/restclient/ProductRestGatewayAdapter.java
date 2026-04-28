package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.util.Base64Utils;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ProductRestGatewayAdapter implements ProductRestGateway {

    private static final Logger log = LoggerFactory.getLogger(ProductRestGatewayAdapter.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
        new ParameterizedTypeReference<Map<String, Object>>() {};

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
    @Timed("product.rest")
    public Flux<ProductInfo> getAllProducts(String traceId) {
        log.info("Fetching all products from REST API, traceId: {}", traceId);

        return webClient.get()
            .uri(properties.productsPath())
            .accept(MediaType.APPLICATION_JSON)
            .header(ApiConstants.HEADER_TRACE_ID, traceId)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
            .map(list -> list.stream().map(this::mapToProductInfo).toList())
            .flatMapMany(Flux::fromIterable)
            .doOnNext(product -> log.info("Product retrieved: {}", product.getProductId()));
    }

    @Override
    public Mono<ProductDocumentInfo> getDocument(String productId, String documentId, String traceId) {
        log.info("Fetching document {} for product {} from REST API, traceId: {}", documentId, productId, traceId);

        String path = properties.productDocumentsPath().replace("{productId}", productId);

        return webClient.get()
            .uri(path + "/{documentId}", documentId)
            .accept(MediaType.APPLICATION_JSON)
            .header(ApiConstants.HEADER_TRACE_ID, traceId)
            .retrieve()
            .bodyToMono(MAP_TYPE_REF)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
            .map(this::mapToProductDocumentInfo)
            .doOnNext(doc -> log.info("Document {} retrieved for product {}", documentId, productId));
    }

    private ProductInfo mapToProductInfo(Map<String, Object> json) {
        Object docsObj = json.get("documents");
        List<ProductDocumentInfo> documents = (docsObj instanceof List<?>)
            ? ((List<?>) docsObj).stream()
                .filter(m -> m instanceof Map)
                .map(m -> mapToProductDocumentInfo((Map<String, Object>) m))
                .toList()
            : List.of();

        return ProductInfo.builder()
            .productId((String) json.get("productId"))
            .name((String) json.get("name"))
            .documents(documents)
            .build();
    }

    private ProductDocumentInfo mapToProductDocumentInfo(Map<String, Object> json) {
        String contentBase64 = (String) json.get("content");
        String filename = (String) json.get("filename");
        String documentId = (String) json.get("documentId");

        byte[] content;
        if (contentBase64 != null && !contentBase64.isBlank()) {
            try {
                content = Base64Utils.decodeSafe(contentBase64, filename, documentId);
            } catch (Exception e) {
                log.warn("Failed to decode Base64 for document {} ({}): {}",
                    documentId, filename, e.getMessage());
                content = new byte[0];
            }
        } else {
            content = new byte[0];
        }

        Object sizeObj = json.get("size");
        long size = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : (long) content.length;

        Object isZipObj = json.get("isZip");
        boolean isZip = isZipObj instanceof Boolean ? (Boolean) isZipObj : false;

        return new ProductDocumentInfo(
            documentId,
            filename,
            content,
            (String) json.get("contentType"),
            size,
            isZip,
            (String) json.get("origin")
        );
    }
}
