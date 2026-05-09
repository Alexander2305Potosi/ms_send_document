package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoapMapperTest {

    @Mock
    private SoapEnvelopeWrapper envelopeWrapper;

    @Mock
    private JAXBContext jaxbContext;

    @Mock
    private Marshaller marshaller;

    private SoapMapper soapMapper;
    private SoapProperties properties;

    @BeforeEach
    void setUp() throws jakarta.xml.bind.JAXBException {
        properties = new SoapProperties(
            "http://test.com", "SYS", "user", "u-token", "h-ns", "b-ns",
            "dest-name", "dest-ns", "dest-op", "soap-action",
            List.of(), Map.of(), Map.of(), 30, 1
        );

        soapMapper = new SoapMapper(envelopeWrapper, jaxbContext);
    }

    @Test
    void parseResponse_mapsToExternalServiceResponse() {
        TransmitirDocumentoResponse soapResponse = new TransmitirDocumentoResponse();
        soapResponse.setStatus("SUCCESS");
        soapResponse.setCorrelationId("corr-123");
        soapResponse.setMessage("OK");

        when(envelopeWrapper.unwrapResponse(anyString(), eq(TransmitirDocumentoResponse.class)))
            .thenReturn(soapResponse);

        ExternalServiceResponse result = soapMapper.parseResponse("<xml/>", "trace-1");

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("corr-123", result.getCorrelationId());
        assertTrue(result.isSuccess());
    }

    @Test
    void buildEnvelope_shouldEscapeSpecialCharacters() throws Exception {
        // For this test, we use a real JAXBContext to verify that JAXB actually escapes the characters.
        // We create a local SoapMapper instance with a real context.
        JAXBContext realContext = JAXBContext.newInstance(
            com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope.class,
            com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapRequestHeader.class,
            com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoRequest.class
        );
        SoapMapper realMapper = new SoapMapper(envelopeWrapper, realContext);

        FileUploadRequest request = FileUploadRequest.builder()
            .filename("test & file.pdf")
            .content(new byte[]{1, 2, 3})
            .subTipoDocumental("SUB123")
            .build();

        String result = realMapper.buildEnvelope(request, properties, "trace-123");

        assertNotNull(result);
        assertTrue(result.contains("test &amp; file.pdf"), "Filename should be escaped in XML");
        assertFalse(result.contains("test & file.pdf"), "Filename should NOT contain unescaped '&'");
    }
}
