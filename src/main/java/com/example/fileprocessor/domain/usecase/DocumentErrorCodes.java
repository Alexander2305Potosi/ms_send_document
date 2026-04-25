package com.example.fileprocessor.domain.usecase;

/**
 * Error code constants for document processing.
 */
public final class DocumentErrorCodes {

    private DocumentErrorCodes() {}

    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String CLIENT_ERROR = "CLIENT_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    public static final String SKIPPED_FOLDER = "SKIPPED_FOLDER";
    public static final String NOT_SENT_ORIGIN = "NOT_SENT_ORIGIN";
    public static final String SIZE_EXCEEDED = "SIZE_EXCEEDED";
}