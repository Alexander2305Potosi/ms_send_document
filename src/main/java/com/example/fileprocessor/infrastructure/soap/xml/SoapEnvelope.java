package com.example.fileprocessor.infrastructure.soap.xml;

public final class SoapEnvelope {

    private static final String SOAP_HEADER = """
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:file="http://example.com/fileservice">
  <soap:Header/>
  <soap:Body>
""";

    private static final String SOAP_FOOTER = """
  </soap:Body>
</soap:Envelope>
""";

    private SoapEnvelope() {}

    public static String wrap(String bodyContent) {
        return SOAP_HEADER + bodyContent + SOAP_FOOTER;
    }
}
