package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for fetching products from external REST API.
 * Abstraction that allows different implementations (mock, real, etc.)
 */
public interface ProductRestGateway {
    /**
     * Fetches all products from the external REST API.
     * @param traceId the trace identifier for request tracking
     * @return Flux of products
     */
    Flux<ProductInfo> getAllProducts(String traceId);

    /**
     * Fetches a specific document for a product.
     * @param productId the product identifier
     * @param documentId the document identifier
     * @param traceId the trace identifier for request tracking
     * @return Mono with document info
     */
    Mono<ProductDocumentInfo> getDocument(String productId, String documentId, String traceId);
}
