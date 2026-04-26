package com.example.fileprocessor.domain.port.out;

/**
 * Port for tracking asynchronous operation status.
 */
public interface AsyncOperationRepository {
    /**
     * Saves the initial status of an async operation.
     */
    void save(com.example.fileprocessor.domain.entity.AsyncOperationStatus status);

    /**
     * Updates the progress of an async operation.
     */
    void updateProgress(String traceId, int processed, int success, int failed);

    /**
     * Marks an async operation as completed.
     */
    void markCompleted(String traceId);

    /**
     * Finds an async operation by traceId.
     * Returns null if not found.
     */
    com.example.fileprocessor.domain.entity.AsyncOperationStatus findByTraceId(String traceId);
}