package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Flux<DocumentHistory> findByDocumentId(String documentId);
    Flux<DocumentHistory> findByState(String state);
    Mono<Void> updateState(String documentId, String state, String errorMessage);
    Mono<Integer> getRetryCount(String documentId, String useCase);
}
