package com.example.fileprocessor.infrastructure.helpers.soap;

/**
 * SOAP envelope and namespace constants.
 */
public final class SoapConstants {

    private SoapConstants() {}

    public static final String SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String FILE_SERVICE = "http://example.com/fileservice";
    public static final String SOAP_ACTION_UPLOAD = "/UploadFile";

    public static final String HEADER_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    public static final String ENVELOPE_START = "<soap:Envelope xmlns:soap=\"";
    public static final String ENVELOPE_END = "\">\n  <soap:Header/>\n  <soap:Body>\n";
    public static final String FOOTER_ENVELOPE_END = "  </soap:Body>\n</soap:Envelope>\n";

    public static final String MSG_SOAP_BODY_NOT_FOUND = "SOAP Body not found";
    public static final String MSG_RESPONSE_ELEMENT_NOT_FOUND = "Response element not found in SOAP Body";
    public static final String MSG_PARSE_ERROR = "Failed to parse SOAP response";
}
