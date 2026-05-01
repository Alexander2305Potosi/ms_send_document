package com.example.fileprocessor.infrastructure.drivenadapters.aws;

/**
 * Error codes specific to S3 operations.
 */
public final class S3ErrorCodes {

    private S3ErrorCodes() {}

    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String CLIENT_ERROR = "CLIENT_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
}
