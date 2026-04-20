package com.example.fileprocessor.infrastructure.soap;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.infrastructure.soap.adapter.ExternalSoapGatewayImpl;
import com.example.fileprocessor.infrastructure.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.soap.mapper.SoapMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalSoapGatewayImplTest {

    private MockWebServer mockWebServer;
    private ExternalSoapGatewayImpl gateway;
    private SoapMapper soapMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        SoapEnvelopeWrapper envelopeWrapper = new SoapEnvelopeWrapper();
        soapMapper = new SoapMapper(envelopeWrapper);

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
        String responseXml = "<?xml version=\"1.0\"?\u003e" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\u003e" +
            "<soap:Body\u003e" +
            "<response\u003e" +
            "<status\u003eSUCCESS</status\u003e" +
            "<message\u003eFile uploaded</message\u003e" +
            "<correlationId\u003e123-abc</correlationId\u003e" +
            "<processedAt\u003e" + Instant.now().toString() + "</processedAt\u003e" +
            "</response\u003e" +
            "</soap:Body\u003e" +
            "</soap:Envelope\u003e";

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
                assertTrue(response.correlationId().equals("123-abc"));
            })
            .verifyComplete();
    }

    @Test
    void sendFile_shouldReturnError_whenServerError() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<?xml version=\"1.0\"?\u003e\u003csoap:Fault\u003e\u003c/faultstring\u003eServer Error\u003c/faultstring\u003e\u003c/soap:Fault\u003e"));

        SoapRequest request = new SoapRequest(
            "base64content",
            "test.pdf",
            "application/pdf",
            100,
            "trace-123",
            Instant.now()
        );

        StepVerifier.create(gateway.sendFile(request))
            .expectError(SoapCommunicationException.class)
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
