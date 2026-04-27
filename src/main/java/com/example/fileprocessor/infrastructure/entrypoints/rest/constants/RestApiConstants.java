package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

/**
 * REST API constants for Product endpoints.
 */
public final class RestApiConstants {

    private RestApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // Query parameter names
    public static final String PARAM_PROCESSOR = "processor";

    // Error messages
    public static final String MSG_S3_NOT_AVAILABLE = "S3 processor is not available. Please enable the 's3' profile.";
    public static final String MSG_UNKNOWN_PROCESSOR = "Unknown processor type '{}', defaulting to SOAP";

    // HTTP headers
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    // Operation types
    public static final String OPERATION_LOAD = "LOAD";
    public static final String OPERATION_PROCESS = "PROCESS";

    // Status values
    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // Message constants
    public static final String MSG_LOADING = "Product loading from REST API started";
    public static final String MSG_PROCESSING = "Pending product documents processing started";
    public static final String MSG_NOT_FOUND = "Operation not found for traceId: ";
}