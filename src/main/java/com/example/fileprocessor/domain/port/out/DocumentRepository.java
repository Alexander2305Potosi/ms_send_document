package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Port for managing document metadata and lifecycle states.
 */
public interface DocumentRepository {
    Mono<Document> save(Document document);
    
    /**
     * Finds documents for the current day.
     */
    Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay);
    
    /**
     * Updates document state, retry count and timestamp.
     */
    Mono<Long> updateStateAndRetry(Long id, String expectedState, String newState, 
                                  Integer retryCount, LocalDateTime updatedAt);

    /**
     * Resets stale IN_PROGRESS documents from today back to PENDING.
     */
    Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold);

    /**
     * Checks if a document with the given productId and documentId already exists.
     */
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);
}
