package com.example.fileprocessor.domain.usecase;

/**
 * Deterministic idempotency key for document processing requests.
 * Format: doc-{documentId}-{traceId}-attempt-{attempt}
 */
public class IdempotencyKey {

    private final String value;

    private IdempotencyKey(String documentId, String traceId, int attempt) {
        this.value = String.format("doc-%s-%s-attempt-%d", documentId, traceId, attempt);
    }

    public static IdempotencyKey forFirstAttempt(String documentId, String traceId) {
        return new IdempotencyKey(documentId, traceId, 1);
    }

    public IdempotencyKey nextAttempt() {
        String[] parts = value.split("-attempt-");
        int current = Integer.parseInt(parts[parts.length - 1]);
        return new IdempotencyKey(
            extractDocId(value),
            extractTraceId(value),
            current + 1);
    }

    public String value() {
        return value;
    }

    /**
     * For recovery scenarios when traceId is lost.
     */
    public static IdempotencyKey forRecovery(String documentId, String newTraceId) {
        return new IdempotencyKey(documentId, newTraceId, 0);
    }

    private static String extractDocId(String key) {
        String[] parts = key.split("-");
        return parts.length >= 2 ? parts[1] : "";
    }

    private static String extractTraceId(String key) {
        String[] parts = key.split("-");
        return parts.length >= 3 ? parts[2] : "";
    }
}
