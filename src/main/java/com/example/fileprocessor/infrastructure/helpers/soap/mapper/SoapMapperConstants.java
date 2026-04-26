package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

/**
 * Constants for SOAP envelope generation.
 */
public final class SoapMapperConstants {

    private SoapMapperConstants() {}

    public static final String SOAP_HEADER_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    public static final String SOAP_HEADER_ENVELOPE_START = "<soap:Envelope xmlns:soap=\"";
    public static final String SOAP_HEADER_ENVELOPE_END = "\">\n  <soap:Header/>\n  <soap:Body>\n";
    public static final String SOAP_FOOTER_ENVELOPE_END = "  </soap:Body>\n</soap:Envelope>\n";
}