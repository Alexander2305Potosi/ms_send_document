package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.AsyncOperationStatus;
import reactor.core.publisher.Mono;

/**
 * Port for tracking asynchronous operation status.
 * All operations are reactive for proper WebFlux integration.
 */
public interface AsyncOperationRepository {
    /**
     * Saves the initial status of an async operation.
     */
    Mono<Void> save(AsyncOperationStatus status);

    /**
     * Updates the progress of an async operation atomically.
     */
    Mono<Void> updateProgress(String traceId, int processed, int success, int failed);

    /**
     * Marks an async operation as completed.
     */
    Mono<Void> markCompleted(String traceId);

    /**
     * Finds an async operation by traceId.
     * Returns empty Mono if not found.
     */
    Mono<AsyncOperationStatus> findByTraceId(String traceId);
}