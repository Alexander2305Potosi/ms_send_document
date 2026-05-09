package com.example.fileprocessor.domain.usecase;

/**
 * Business-level error codes for document processing operations.
 */
public enum ProcessingResultCodes {
    INVALID_BASE64,
    EMPTY_CONTENT,
    INVALID_RESPONSE,
    DECOMPRESSION_ERROR,
    SIZE_EXCEEDED,
    PATTERN_MISMATCH,
    UNKNOWN_ERROR,
    BAD_GATEWAY,
    GATEWAY_TIMEOUT,
    SERVICE_UNAVAILABLE;
}
