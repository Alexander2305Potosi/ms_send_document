package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Flux<DocumentHistory> findByStateAndUseCase(String state, String useCase);
    Flux<DocumentHistory> findByDocumentIdAndStateAndUseCase(String documentId, String state, String useCase);
    Mono<Void> updateStateAndUseCase(String documentId, String state, String useCase);
    Mono<Void> updateWithAudit(String documentId, String state, String errorCode, String errorMessage, int retry, String useCase, String stackTrace, LocalDateTime completedAt);
    Mono<DocumentHistory> findLastAudit(String documentId, String useCase);
}
