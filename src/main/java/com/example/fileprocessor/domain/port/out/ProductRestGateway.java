package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for fetching products from external REST API.
 */
public interface ProductRestGateway {
    Flux<Document> getDocumentsByProduct(ProductMaestro product);
    Mono<ProductDocumentFile> getDocument(String productId, String documentId);
}
