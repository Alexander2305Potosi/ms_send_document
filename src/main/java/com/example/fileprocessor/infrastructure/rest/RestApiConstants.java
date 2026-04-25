package com.example.fileprocessor.infrastructure.rest;

/**
 * REST API constants for Product endpoints.
 */
public final class RestApiConstants {

    private RestApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // Status values
    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_PROCESSING = "PROCESSING";

    // Message constants
    public static final String MSG_LOADING = "Product loading from REST API started";
    public static final String MSG_PROCESSING = "Pending product documents processing started";
}