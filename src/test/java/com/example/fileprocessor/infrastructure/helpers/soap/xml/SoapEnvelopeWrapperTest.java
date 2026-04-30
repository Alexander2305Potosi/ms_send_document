package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SoapEnvelopeWrapperTest {

    private SoapEnvelopeWrapper wrapper;

    @BeforeEach
    void setUp() {
        wrapper = new SoapEnvelopeWrapper();
    }

    @Test
    void unwrapResponse_shouldParseValidSoapResponse() {
        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header/>
              <soap:Body>
                <file:UploadFileResponse xmlns:file="http://example.com/fileservice">
                  <file:status>SUCCESS</file:status>
                  <file:message>Upload successful</file:message>
                  <file:correlationId>abc-123</file:correlationId>
                  <file:processedAt>%s</file:processedAt>
                  <file:externalReference>ref-001</file:externalReference>
                </file:UploadFileResponse>
              </soap:Body>
            </soap:Envelope>
            """.formatted(Instant.now().toString());

        UploadFileResponse response = wrapper.unwrapResponse(validXml, UploadFileResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Upload successful", response.getMessage());
        assertEquals("abc-123", response.getCorrelationId());
    }

    @Test
    void unwrapResponse_shouldThrowWhenBodyMissing() {
        String xmlWithoutBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header/>
            </soap:Envelope>
            """;

        ProcessingException exception = assertThrows(
            ProcessingException.class,
            () -> wrapper.unwrapResponse(xmlWithoutBody, UploadFileResponse.class)
        );

        assertTrue(exception.getMessage().contains("Body not found"));
    }

    @Test
    void unwrapResponse_shouldThrowWhenResponseElementMissing() {
        String xmlWithEmptyBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header/>
              <soap:Body/>
            </soap:Envelope>
            """;

        ProcessingException exception = assertThrows(
            ProcessingException.class,
            () -> wrapper.unwrapResponse(xmlWithEmptyBody, UploadFileResponse.class)
        );

        assertTrue(exception.getMessage().contains("Response element not found"));
    }

    @Test
    void unwrapResponse_shouldThrowOnInvalidXml() {
        String invalidXml = "not xml at all";

        ProcessingException exception = assertThrows(
            ProcessingException.class,
            () -> wrapper.unwrapResponse(invalidXml, UploadFileResponse.class)
        );

        assertNotNull(exception);
    }

    @Test
    void getJaxbContext_shouldReturnInitializedContext() {
        assertNotNull(wrapper.getJaxbContext());
    }
}
