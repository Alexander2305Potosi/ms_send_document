package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentTraceability;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentTraceabilityGateway {
    Mono<Void> save(DocumentTraceability record);
    Flux<DocumentTraceability> findByProductId(String productId);
    Flux<DocumentTraceability> findByStatus(String status);
}
