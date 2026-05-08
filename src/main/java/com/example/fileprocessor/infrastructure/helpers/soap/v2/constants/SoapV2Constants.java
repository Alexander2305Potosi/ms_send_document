package com.example.fileprocessor.infrastructure.helpers.soap.v2.constants;

/**
 * SOAP V2 envelope constants. Only contains W3C-standard URIs, XML prefixes,
 * and element names from the WSDL contract. Vendor-specific namespace URIs
 * are externalized to {@code SoapV2Properties}.
 *
 * <p>Element-level constants for header child elements have been removed;
 * element names are now defined directly on JAXB {@code @XmlElement} annotations
 * in the model classes.
 */
public final class SoapV2Constants {

    private SoapV2Constants() {}

    public static final String SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String PREFIX_HEADER_NS = "v2";
    public static final String PREFIX_BODY_NS = "v1";

    public static final String EL_ENVELOPE          = "Envelope";
    public static final String EL_HEADER            = "Header";
    public static final String EL_BODY              = "Body";
    public static final String EL_REQUEST_HEADER    = "requestHeader";
    public static final String EL_TRANSMITIR_DOCUMENTO = "transmitirDocumento";
}
