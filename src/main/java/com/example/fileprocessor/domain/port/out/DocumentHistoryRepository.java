package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory record);
    Flux<DocumentHistory> findByProductId(String productId);
    Flux<DocumentHistory> findByStatus(String status);
}