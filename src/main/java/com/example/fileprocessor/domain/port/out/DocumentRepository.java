package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentToProcess;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentRepository {
    Flux<DocumentToProcess> findPendingDocuments();
    Mono<Boolean> claimDocument(String documentId);
    Mono<Void> updateStatus(String documentId, String status, String traceId, String soapCorrelationId, String errorCode);

    /**
     * Saves a document to the database for later processing.
     *
     * @param document the document to save
     * @return Mono<Void> completion signal
     */
    Mono<Void> save(DocumentToProcess document);
}
