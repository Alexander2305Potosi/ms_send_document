package com.example.fileprocessor.domain.entity;

import java.time.Instant;

/**
 * Universal command object for document state transitions and auditing.
 * Replaces FinalizeProcessingCommand and other specific update objects.
 */
public record DocumentUpdateCommand(
    Document document,
    String expectedState,
    String newState,
    int nextRetryCount,
    FileUploadResponse response, // Nullable for transitions without immediate result
    Instant startTime           // Nullable for transitions without audit timing
) {
    /**
     * Creates a command for locking a document (PENDING -> IN_PROGRESS).
     */
    public static DocumentUpdateCommand lock(Document doc) {
        return new DocumentUpdateCommand(doc, ProductState.PENDING, ProductState.IN_PROGRESS, doc.getRetryCountSafe(), null, null);
    }

    /**
     * Creates a command for finalizing a process (IN_PROGRESS -> PROCESSED/FAILED/PENDING).
     */
    public static DocumentUpdateCommand finalize(Document doc, FileUploadResponse response, String finalState, int nextRetry, Instant start) {
        return new DocumentUpdateCommand(doc, ProductState.IN_PROGRESS, finalState, nextRetry, response, start);
    }

    public String getErrorMessage() {
        return response != null ? response.getMessage() : null;
    }
}