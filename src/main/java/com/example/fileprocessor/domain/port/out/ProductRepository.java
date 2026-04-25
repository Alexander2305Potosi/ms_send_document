package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductToProcess;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepository {
    Flux<ProductToProcess> findPendingProducts();
    Mono<Void> save(ProductToProcess product);
    Mono<Void> updateStatus(String productId, String status, String traceId);
}
