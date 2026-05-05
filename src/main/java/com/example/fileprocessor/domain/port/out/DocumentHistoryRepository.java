package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Flux<DocumentHistory> findByDocumentId(String documentId);
    Flux<DocumentHistory> findByState(String state);
    Flux<DocumentHistory> findByStateAndUseCase(String state, String useCase);
    Flux<DocumentHistory> findByDocumentIdAndStateAndUseCase(String documentId, String state, String useCase);
    Mono<Void> updateState(String documentId, String state, String errorMessage);
    Mono<Void> updateStateAndUseCase(String documentId, String state, String useCase);
    Mono<Void> updateWithAudit(String documentId, String state, String errorCode, String errorMessage, int retry, String useCase);
    Mono<Integer> getRetryCount(String documentId, String useCase);
    Mono<DocumentHistory> findLastAudit(String documentId);
    Mono<DocumentHistory> findLastAudit(String documentId, String useCase);
}
