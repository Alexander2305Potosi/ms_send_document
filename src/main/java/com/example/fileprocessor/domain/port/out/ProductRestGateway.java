package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for fetching products from external REST API.
 */
public interface ProductRestGateway {
    Flux<Product> getAllProducts();
    Mono<ProductDocument> getDocument(String productId, String documentId);
}
