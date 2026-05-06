package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Flux<DocumentHistory> findByStateAndUseCase(String state, String useCase);
    Mono<DocumentHistory> findLastAudit(String documentId, String useCase);
    Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt);
    Mono<Void> updateWithAuditById(Long id, String state, String errorCode, String errorMessage, int retry, String stackTrace, LocalDateTime completedAt);
}
