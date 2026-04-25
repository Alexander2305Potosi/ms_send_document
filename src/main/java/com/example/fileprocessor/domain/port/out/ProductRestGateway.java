package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRestGateway {
    Flux<ProductInfo> getAllProducts(String traceId);
    Mono<ProductDocumentInfo> getDocument(String productId, String documentId, String traceId);
}
