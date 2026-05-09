package com.example.fileprocessor.infrastructure.helpers.soap.constants;

/**
 * Unified SOAP constants for envelope structure and error messages.
 */
public final class SoapConstants {

    private SoapConstants() {}

    // ── Namespaces ────────────────────────────────────────────────────────
    public static final String NS_SOAPENV = "http://example.com/header";
    public static final String NS_V2 = "http://example.com/body";
    public static final String NS_V1 = "http://schemas.xmlsoap.org/soap/envelope/";

    // ── Prefixes ──────────────────────────────────────────────────────────
    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String PREFIX_V2 = "v2";
    public static final String PREFIX_V1 = "v1";

    // ── Elements ──────────────────────────────────────────────────────────
    public static final String EL_ENVELOPE          = "Envelope";
    public static final String EL_HEADER            = "Header";
    public static final String EL_BODY              = "Body";
    public static final String EL_REQUEST_HEADER    = "requestHeader";
    public static final String EL_TRANSMITIR_DOCUMENTO_REQUEST = "transmitirDocumentoRequest";

    // ── Error Messages ────────────────────────────────────────────────────
    public static final String MSG_SOAP_BODY_NOT_FOUND = "SOAP Body not found in response";
    public static final String MSG_RESPONSE_ELEMENT_NOT_FOUND = "Response element not found inside SOAP Body";
    public static final String MSG_PARSE_ERROR = "Error parsing SOAP response";
}
