package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

/**
 * API-level constants for REST endpoints and integrations.
 */
public final class ApiConstants {

    private ApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // HTTP headers and query params
    public static final String HEADER_TRACE_ID = "message-id";
    public static final String HEADER_USE_CASE = "use-case";
    public static final String HEADER_DATE_INIT = "date_init";
    public static final String HEADER_DATE_END = "date_end";
    public static final String HEADER_PRODUCT_STATUS = "product_status";
    public static final String TYPE_JOB = "type_job";

    // Respuestas de estado del proceso
    public static final String STATUS_IN_PROGRESS = "1";
    public static final String STATUS_COMPLETED = "exitoso";
    public static final String STATUS_ERROR = "error";
}
