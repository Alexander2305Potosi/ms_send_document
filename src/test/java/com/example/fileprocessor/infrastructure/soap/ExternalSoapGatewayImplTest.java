package com.example.fileprocessor.infrastructure.soap;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.infrastructure.soap.adapter.ExternalSoapGatewayImpl;
import com.example.fileprocessor.infrastructure.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileResponse;
import jakarta.xml.bind.JAXBContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalSoapGatewayImplTest {

    private MockWebServer mockWebServer;
    private ExternalSoapGatewayImpl gateway;
    private SoapMapper soapMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        JAXBContext context = JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);
        SoapEnvelopeWrapper envelopeWrapper = new SoapEnvelopeWrapper(context);
        soapMapper = new SoapMapper(envelopeWrapper, context);

        SoapProperties properties = new SoapProperties(
            mockWebServer.url("/").toString(),
            5,
            1,
            100
        );

        gateway = new ExternalSoapGatewayImpl(
            WebClient.builder(),
            properties,
            soapMapper
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void sendFile_shouldReturnResponse_whenSuccess() {
        String responseXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:file=\"http://example.com/fileservice\">" +
            "<soap:Header/>" +
            "<soap:Body>" +
            "<file:UploadFileResponse>" +
            "<file:status>SUCCESS</file:status>" +
            "<file:message>File uploaded</file:message>" +
            "<file:correlationId>123-abc</file:correlationId>" +
            "<file:processedAt>" + Instant.now().toString() + "</file:processedAt>" +
            "<file:externalReference>ext-ref-123</file:externalReference>" +
            "</file:UploadFileResponse>" +
            "</soap:Body>" +
            "</soap:Envelope>";

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(responseXml)
            .addHeader("Content-Type", "text/xml"));

        SoapRequest request = new SoapRequest(
            "base64content",
            "test.pdf",
            "application/pdf",
            100,
            "trace-123",
            Instant.now()
        );

        StepVerifier.create(gateway.sendFile(request))
            .assertNext(response -> {
                assertTrue(response.isSuccess());
                assertEquals("123-abc", response.correlationId());
            })
            .verifyComplete();
    }

    @Test
    void sendFile_shouldReturnError_whenServerError() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<?xml version=\"1.0\"?><soap:Fault></faultstring>Server Error</faultstring></soap:Fault>"));

        SoapRequest request = new SoapRequest(
            "base64content",
            "test.pdf",
            "application/pdf",
            100,
            "trace-123",
            Instant.now()
        );

        StepVerifier.create(gateway.sendFile(request))
            .expectErrorMatches(throwable -> throwable instanceof SoapCommunicationException)
            .verify();
    }

    @Test
    void sendFile_shouldReturnError_whenTimeout() {
        mockWebServer.enqueue(new MockResponse()
            .setHeadersDelay(10, TimeUnit.SECONDS));

        SoapRequest request = new SoapRequest(
            "base64content",
            "test.pdf",
            "application/pdf",
            100,
            "trace-123",
            Instant.now()
        );

        StepVerifier.create(gateway.sendFile(request))
            .expectError()
            .verify();
    }
}
