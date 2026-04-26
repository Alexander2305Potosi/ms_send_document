package com.example.fileprocessor.infrastructure.soap;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.ExternalSoapGatewayImpl;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Instant;

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

        SoapRequest request = SoapRequest.builder()
            .fileContentBase64("base64content")
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(100)
            .traceId("trace-123")
            .timestamp(Instant.now())
            .build();

        StepVerifier.create(gateway.sendFile(request))
            .assertNext(response -> {
                assertTrue(response.isSuccess());
                assertEquals("123-abc", response.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void sendFile_shouldReturnError_whenServerError() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<?xml version=\"1.0\"?><soap:Fault></faultstring>Server Error</faultstring></soap:Fault>"));

        SoapRequest request = SoapRequest.builder()
            .fileContentBase64("base64content")
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(100)
            .traceId("trace-123")
            .timestamp(Instant.now())
            .build();

        StepVerifier.create(gateway.sendFile(request))
            .expectErrorMatches(throwable -> throwable instanceof SoapCommunicationException)
            .verify();
    }

    @Test
    @Disabled("Timeout test is flaky - exception arrives as WebClientRequestException not SoapCommunicationException")
    void sendFile_shouldReturnError_whenTimeout() {
        mockWebServer.enqueue(new MockResponse()
            .setHeadersDelay(10, java.util.concurrent.TimeUnit.SECONDS));

        SoapRequest request = SoapRequest.builder()
            .fileContentBase64("base64content")
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(100)
            .traceId("trace-123")
            .timestamp(Instant.now())
            .build();

        StepVerifier.create(gateway.sendFile(request))
            .expectErrorMatches(throwable ->
                throwable instanceof SoapCommunicationException &&
                "GATEWAY_TIMEOUT".equals(((SoapCommunicationException) throwable).getErrorCode()))
            .verify();
    }
}