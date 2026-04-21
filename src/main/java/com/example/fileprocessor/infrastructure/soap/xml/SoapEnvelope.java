package com.example.fileprocessor.infrastructure.soap.xml;

public final class SoapEnvelope {

    private static final String SOAP_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?\u003e\n" +
        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
        "               xmlns:file=\"http://example.com/fileservice\"\u003e\n" +
        "  <soap:Header/\u003e\n" +
        "  <soap:Body\u003e\n";

    private static final String SOAP_FOOTER = "  </soap:Body\u003e\n" +
        "</soap:Envelope\u003e";

    private SoapEnvelope() {}

    public static String wrap(String bodyContent) {
        return SOAP_HEADER + bodyContent + SOAP_FOOTER;
    }
}
