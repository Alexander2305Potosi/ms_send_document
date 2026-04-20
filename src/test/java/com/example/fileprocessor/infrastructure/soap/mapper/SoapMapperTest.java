package com.example.fileprocessor.infrastructure.soap.mapper;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.infrastructure.soap.xml.SoapEnvelopeWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SoapMapperTest {

    private SoapMapper soapMapper;
    private SoapEnvelopeWrapper envelopeWrapper;

    @BeforeEach
    void setUp() throws Exception {
        envelopeWrapper = new SoapEnvelopeWrapper();
        soapMapper = new SoapMapper(envelopeWrapper);
    }

    @Test
    void toSoapXml_shouldGenerateValidSoapBody() {
        SoapRequest request = new SoapRequest(
            "dGVzdENvbnRlbnQ=", // base64
            "test.pdf",
            "application/pdf",
            1234,
            "trace-123",
            Instant.now()
        );

        String xml = soapMapper.toSoapXml(request);

        assertNotNull(xml);
        // Ahora solo genera el body, el envelope se agrega en el gateway
        assertFalse(xml.contains("soap:Envelope"), "Should NOT contain SOAP envelope");
        assertTrue(xml.contains("UploadFileRequest"));
        assertTrue(xml.contains("test.pdf"));
        assertTrue(xml.contains("trace-123"));
        assertTrue(xml.contains("http://example.com/fileservice"));
    }

    @Test
    void fromSoapXml_shouldParseValidSoapResponse() {
        String soapResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                         xmlns:file="http://example.com/fileservice">
              <soap:Header/>
              <soap:Body>
                <file:UploadFileResponse>
                  <file:status>SUCCESS</file:status>
                  <file:message>File processed</file:message>
                  <file:correlationId>corr-abc-123</file:correlationId>
                  <file:processedAt>2024-01-15T10:30:00Z</file:processedAt>
                  <file:externalReference>ext-ref-456</file:externalReference>
                </file:UploadFileResponse>
              </soap:Body>
            </soap:Envelope>
            """;

        SoapResponse response = soapMapper.fromSoapXml(soapResponse, "trace-123");

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertEquals("File processed", response.message());
        assertEquals("corr-abc-123", response.correlationId());
        assertEquals("trace-123", response.traceId());
        assertEquals("ext-ref-456", response.externalReference());
        assertTrue(response.isSuccess());
    }

    @Test
    void fromSoapXml_shouldHandleMissingOptionalFields() {
        String soapResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                         xmlns:file="http://example.com/fileservice">
              <soap:Header/>
              <soap:Body>
                <file:UploadFileResponse>
                  <file:status>OK</file:status>
                  <file:message>Done</file:message>
                  <file:correlationId>corr-xyz</file:correlationId>
                </file:UploadFileResponse>
              </soap:Body>
            </soap:Envelope>
            """;

        SoapResponse response = soapMapper.fromSoapXml(soapResponse, "trace-456");

        assertNotNull(response);
        assertEquals("OK", response.status());
        assertNull(response.externalReference());
        assertTrue(response.isSuccess());
    }

    @Test
    void fromSoapXml_shouldHandleErrorResponse() {
        String soapResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                         xmlns:file="http://example.com/fileservice">
              <soap:Header/>
              <soap:Body>
                <file:UploadFileResponse>
                  <file:status>ERROR</file:status>
                  <file:message>Processing failed</file:message>
                  <file:correlationId>corr-error</file:correlationId>
                </file:UploadFileResponse>
              </soap:Body>
            </soap:Envelope>
            """;

        SoapResponse response = soapMapper.fromSoapXml(soapResponse, "trace-789");

        assertNotNull(response);
        assertEquals("ERROR", response.status());
        assertFalse(response.isSuccess());
    }

    @Test
    void fromSoapXml_shouldHandleInvalidXml() {
        String invalidXml = "not valid xml";

        SoapResponse response = soapMapper.fromSoapXml(invalidXml, "trace-000");

        assertNotNull(response);
        assertEquals("ERROR", response.status());
        assertEquals("N/A", response.correlationId());
        assertEquals("trace-000", response.traceId());
    }
}
