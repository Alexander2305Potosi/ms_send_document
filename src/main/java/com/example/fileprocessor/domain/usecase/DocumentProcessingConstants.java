package com.example.fileprocessor.domain.usecase;

/**
 * Constants for document processing use cases.
 */
public final class DocumentProcessingConstants {

    private DocumentProcessingConstants() {}

    public static final int DEFAULT_RETRY_COUNT = 0;
    public static final int DEFAULT_MAX_CONCURRENCY = 10;

    // Message constants for document processing results
    public static final String MSG_SKIPPED_FOLDER = "Document skipped due to folder rule: ";
    public static final String MSG_NOT_SENT_ORIGIN = "Document not sent: origin does not match required patterns: ";
    public static final String MSG_SIZE_EXCEEDED = "Document not sent: file size ";
    public static final String MSG_SIZE_EXCEEDED_SUFFIX = " bytes exceeds limit";
    public static final String MSG_CIRCUIT_BREAKER_OPEN = "Circuit breaker is OPEN, document will be retried later";
}