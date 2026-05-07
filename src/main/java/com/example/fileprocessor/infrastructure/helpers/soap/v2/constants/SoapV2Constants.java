package com.example.fileprocessor.infrastructure.helpers.soap.v2.constants;

/**
 * SOAP V2 envelope constants. Only contains W3C-standard URIs, XML prefixes,
 * and element names from the WSDL contract. Vendor-specific namespace URIs
 * are externalized to {@code SoapV2Properties}.
 */
public final class SoapV2Constants {

    private SoapV2Constants() {}

    public static final String SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String PREFIX_V2 = "v2";
    public static final String PREFIX_V1 = "v1";

    public static final String EL_ENVELOPE = "Envelope";
    public static final String EL_HEADER = "Header";
    public static final String EL_BODY = "Body";
    public static final String EL_REQUEST_HEADER = "requestHeader";
    public static final String EL_SYSTEM_ID = "systemId";
    public static final String EL_MESSAGE_ID = "messageId";
    public static final String EL_TIMESTAMP = "timestamp";
    public static final String EL_MESSAGE_CONTEXT = "messageContext";
    public static final String EL_PROPERTY = "property";
    public static final String EL_KEY = "key";
    public static final String EL_VALUE = "value";
    public static final String EL_USER_ID = "userId";
    public static final String EL_USER_NAME = "userName";
    public static final String EL_USER_TOKEN = "userToken";
    public static final String EL_DESTINATION = "destination";
    public static final String EL_DEST_NAME = "name";
    public static final String EL_DEST_NAMESPACE = "namespace";
    public static final String EL_DEST_OPERATION = "operation";
    public static final String EL_CLASSIFICATIONS = "classifications";
    public static final String EL_CLASSIFICATION = "classification";
}
