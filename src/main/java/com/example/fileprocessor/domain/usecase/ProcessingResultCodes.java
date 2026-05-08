package com.example.fileprocessor.domain.usecase;

/**
 * Business-level error codes for document processing operations.
 * Infrastructure-specific codes (HTTP, AWS) are in their respective adapters.
 */
public final class ProcessingResultCodes {

    private ProcessingResultCodes() {}

    // Base64 validation error codes
    public static final String EMPTY_CONTENT = "EMPTY_CONTENT";
    public static final String INVALID_BASE64 = "INVALID_BASE64";

    // Response validation error codes
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    // ZIP decompression error codes
    public static final String DECOMPRESSION_ERROR = "DECOMPRESSION_ERROR";

    // Generic error codes (business level)
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String REST_CLIENT_ERROR = "REST_CLIENT_ERROR";
    public static final String SIZE_EXCEEDED = "SIZE_EXCEEDED";
    public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";
}
