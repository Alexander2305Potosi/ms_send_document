package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapBody;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapMapperTest {

    @Mock
    private JAXBContext jaxbContext;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @Mock
    private Unmarshaller unmarshaller;

    private SoapMapper soapMapper;
    private SoapProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = new SoapProperties(
            "http://test.com", "SYS", "user", "h-ns", "b-ns", "s-ns",
            "u-token", "dest-name", "dest-ns", "dest-op", "soap-action",
            "CLASS-1", Map.of(), Map.of(), 30, 1
        );

        String mockXml = "<xml>{{traceId}}{{filename}}</xml>";
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(mockXml.getBytes(StandardCharsets.UTF_8)));

        soapMapper = new SoapMapper(jaxbContext, properties, resourceLoader);
        soapMapper.init();
    }

    @Test
    void buildEnvelope_shouldReplaceTokens() {
        FileUploadRequest request = FileUploadRequest.builder()
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .subTipoDocumental("SUB1")
            .build();

        String result = soapMapper.buildEnvelope(request, properties, "trace-123");

        assertNotNull(result);
        assertTrue(result.contains("trace-123"));
        assertTrue(result.contains("test.pdf"));
    }

    @Test
    void parseResponse_mapsToExternalServiceResponse() throws Exception {
        TransmitirDocumentoResponse soapResponse = new TransmitirDocumentoResponse();
        soapResponse.setStatus("SUCCESS");
        soapResponse.setCorrelationId("corr-123");
        soapResponse.setMessage("OK");

        SoapEnvelope envelope = new SoapEnvelope(new SoapHeader(), new SoapBody(soapResponse));

        when(jaxbContext.createUnmarshaller()).thenReturn(unmarshaller);
        when(unmarshaller.unmarshal(any(java.io.Reader.class))).thenReturn(envelope);

        ExternalServiceResponse result = soapMapper.parseResponse("<xml/>", "trace-1");

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("corr-123", result.getCorrelationId());
    }
}
