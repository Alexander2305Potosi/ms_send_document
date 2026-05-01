package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapMapperTest {

    private SoapMapper soapMapper;
    private SoapEnvelopeWrapper envelopeWrapper;

    @BeforeEach
    void setUp() {
        envelopeWrapper = new SoapEnvelopeWrapper();
        soapMapper = new SoapMapper(envelopeWrapper);
    }

    @Test
    void toSoapXml_shouldGenerateValidSoapBody() {
        FileUploadRequest request = FileUploadRequest.builder()
            .documentId("doc-1")
            .content("testContent".getBytes())
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(1234L)
            .build();

        String xml = soapMapper.toSoapXml(request);

        assertNotNull(xml);
        assertFalse(xml.contains("soap:Envelope"), "Should NOT contain SOAP envelope");
        assertTrue(xml.contains("UploadFileRequest"));
        assertTrue(xml.contains("test.pdf"));
        assertTrue(xml.contains("http://example.com/fileservice"));
    }

    @Test
    void fromSoapXml_shouldParseValidExternalServiceResponse() {
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

        ExternalServiceResponse response = soapMapper.fromSoapXml(soapResponse);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("File processed", response.getMessage());
        assertEquals("corr-abc-123", response.getCorrelationId());
        assertEquals("ext-ref-456", response.getExternalReference());
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

        ExternalServiceResponse response = soapMapper.fromSoapXml(soapResponse);

        assertNotNull(response);
        assertEquals("OK", response.getStatus());
        assertNull(response.getExternalReference());
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

        ExternalServiceResponse response = soapMapper.fromSoapXml(soapResponse);

        assertNotNull(response);
        assertEquals("ERROR", response.getStatus());
        assertFalse(response.isSuccess());
    }

    @Test
    void fromSoapXml_shouldThrowProcessingException_whenInvalidXml() {
        String invalidXml = "not valid xml";

        ProcessingException exception = assertThrows(ProcessingException.class,
            () -> soapMapper.fromSoapXml(invalidXml));

        assertEquals("INVALID_RESPONSE", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to parse"));
    }
}
