package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for fetching products from external REST API.
 */
public interface ProductRestGateway {
    Flux<ProductDocumentHistory> getAllProducts();
    Mono<ProductDocumentFile> getDocument(String productId, String documentId);
}
