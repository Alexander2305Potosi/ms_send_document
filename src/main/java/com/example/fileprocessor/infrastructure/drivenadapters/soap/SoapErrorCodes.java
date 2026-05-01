package com.example.fileprocessor.infrastructure.drivenadapters.soap;

/**
 * Error codes specific to SOAP operations.
 */
public final class SoapErrorCodes {

    private SoapErrorCodes() {}

    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
}
