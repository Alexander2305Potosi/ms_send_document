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
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

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
