package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
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

    private SoapMapper soapMapper;
    private SoapProperties properties;

    @BeforeEach
    void setUp() {
        soapMapper = new SoapMapper(envelopeWrapper, jaxbContext);
        properties = new SoapProperties(
            "http://test.com", "SYS", "user", "h-ns", "b-ns",
            null, null, null, null, "action", List.of(), Map.of(), Map.of(), 30, 1
        );
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
}
