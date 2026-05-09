package com.example.fileprocessor.domain.usecase;

/**
 * Business-level error codes for document processing operations.
 */
public final class ProcessingResultCodes {

    private ProcessingResultCodes() {}

    public static final String INVALID_BASE64 = "INVALID_BASE64";
    public static final String EMPTY_CONTENT = "EMPTY_CONTENT";
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    public static final String DECOMPRESSION_ERROR = "DECOMPRESSION_ERROR";
    public static final String SIZE_EXCEEDED = "SIZE_EXCEEDED";
    public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
}
