package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.DocumentUpdateCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Domain gateway orchestrating document lifecycle and audit trail.
 */
public interface DocumentPersistenceGateway {
    Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold);

    Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay);

    Mono<Long> lockDocumentForProcessing(Long docId, int currentRetryCount);

    Mono<FileUploadResponse> finalizeProcessingAtomically(DocumentUpdateCommand command);
}
