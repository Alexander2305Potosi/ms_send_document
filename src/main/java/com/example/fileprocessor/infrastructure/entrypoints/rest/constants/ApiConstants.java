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

    // REST operation messages
    public static final String MSG_LOADING = "Product loading from REST API started";
    public static final String MSG_PROCESSING = "Pending product documents processing started";
    public static final String MSG_NOT_FOUND = "Operation not found for traceId: ";

    // SOAP response constants
    public static final String SOAP_STATUS_OK = "OK";

    // Product document constants
    public static final String EXTENSION_ZIP = "zip";
    public static final String DEFAULT_FOLDER = ".";

    // File path characters (for validation)
    public static final String PATH_DOUBLE_DOT = "..";
    public static final String PATH_SLASH = "/";
    public static final String PATH_BACKSLASH = "\\";

    // SOAP envelope constants
    public static final String SOAP_HEADER_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    public static final String SOAP_HEADER_ENVELOPE_START = "<soap:Envelope xmlns:soap=\"";
    public static final String SOAP_HEADER_ENVELOPE_END = "\">\n  <soap:Header/>\n  <soap:Body>\n";
    public static final String SOAP_FOOTER_ENVELOPE_END = "  </soap:Body>\n</soap:Envelope>\n";

    // SOAP error messages
    public static final String MSG_SOAP_BODY_NOT_FOUND = "SOAP Body not found";
    public static final String MSG_RESPONSE_ELEMENT_NOT_FOUND = "Response element not found in SOAP Body";
    public static final String MSG_PARSE_ERROR = "Failed to parse SOAP response";
}
