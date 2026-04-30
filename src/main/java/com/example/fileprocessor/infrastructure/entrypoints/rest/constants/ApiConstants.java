package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

/**
 * API-level constants for REST endpoints and integrations.
 */
public final class ApiConstants {

    private ApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // Implementation names
    public static final String IMPL_NAME_SOAP = "SOAP";
    public static final String IMPL_NAME_S3 = "S3";

    // Query parameter names
    public static final String PARAM_PROCESSOR = "processor";

    // HTTP headers
    public static final String HEADER_TRACE_ID = "message-id";
}
