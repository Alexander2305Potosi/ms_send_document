package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Mono;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Mono<DocumentHistory> findLastAudit(Long documentId, String useCase);
}
