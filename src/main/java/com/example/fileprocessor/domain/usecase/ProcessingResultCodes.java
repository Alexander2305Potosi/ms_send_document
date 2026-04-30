package com.example.fileprocessor.domain.usecase;

/**
 * Error codes for document processing operations.
 * Includes business error codes and HTTP-related error codes.
 */
public final class ProcessingResultCodes {

    private ProcessingResultCodes() {}

    // Base64 validation error codes (P10)
    public static final String EMPTY_CONTENT = "EMPTY_CONTENT";
    public static final String INVALID_BASE64 = "INVALID_BASE64";

    // Response validation error codes
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    // HTTP-related error codes (shared between domain and infrastructure)
    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String CLIENT_ERROR = "CLIENT_ERROR";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    // S3-specific error codes
    public static final String ACCESS_DENIED_ERROR = "ACCESS_DENIED_ERROR";
    public static final String NOT_FOUND_ERROR = "NOT_FOUND_ERROR";
    public static final String SERVICE_UNAVAILABLE_ERROR = "SERVICE_UNAVAILABLE_ERROR";
}
