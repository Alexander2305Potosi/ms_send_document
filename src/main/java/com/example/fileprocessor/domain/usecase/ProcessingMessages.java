package com.example.fileprocessor.domain.usecase;

/**
 * Messages and defaults for document processing.
 */
public final class ProcessingMessages {

    private ProcessingMessages() {}

    // Retry and concurrency defaults
    public static final int DEFAULT_RETRY_COUNT = 0;
    public static final int DEFAULT_MAX_CONCURRENCY = 10;

    // Document processing messages
    public static final String MSG_SKIPPED_FOLDER = "Document skipped due to folder rule: ";
    public static final String MSG_NOT_SENT_ORIGIN = "Document not sent: origin does not match required patterns: ";
    public static final String MSG_SIZE_EXCEEDED = "Document not sent: file size ";
    public static final String MSG_SIZE_EXCEEDED_SUFFIX = " bytes exceeds limit";
    public static final String MSG_CIRCUIT_BREAKER_OPEN = "Circuit breaker is OPEN, document will be retried later";

    // File validation messages
    public static final String MSG_FILE_SIZE_EXCEEDED = "File size exceeds maximum allowed: ";
    public static final String MSG_FILE_TYPE_NOT_ALLOWED = "File type not allowed. Allowed types: ";
    public static final String MSG_FILENAME_TOO_LONG = "Filename exceeds maximum length: ";
    public static final String MSG_FILENAME_INVALID = "Filename contains invalid characters";

    // S3 messages
    public static final String MSG_UPLOAD_SUCCESS = "Uploaded to S3: ";
    public static final String MSG_UPLOAD_FAILURE = "S3 upload failed: ";

    // Load products messages
    public static final String MSG_PRODUCT_LOADED = "Product and documents loaded successfully";

    // Validation message fragments (for error code extraction)
    public static final String MSG_TIMEOUT = "timeout";
    public static final String MSG_TIMEOUT_TITLE = "Timeout";
    public static final String MSG_VALIDATION = "validation";
}
