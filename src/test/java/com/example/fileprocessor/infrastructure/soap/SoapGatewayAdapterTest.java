package com.example.fileprocessor.infrastructure.soap;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.SoapGatewayAdapter;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoapGatewayAdapterTest {

    private MockWebServer mockWebServer;
    private SoapGatewayAdapter gateway;
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

        gateway = new SoapGatewayAdapter(
            WebClient.builder(),
            properties,
            soapMapper
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    private DocumentSendRequest createTestRequest() {
        return DocumentSendRequest.builder()
            .fileContent("base64content".getBytes())
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(100)
            .traceId("trace-123")
            .build();
    }

    @Test
    void send_shouldReturnResult_whenSuccess() {
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

        StepVerifier.create(gateway.send(createTestRequest()))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("123-abc", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void send_shouldReturnError_whenServerError() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<?xml version=\"1.0\"?><soap:Fault></faultstring>Server Error</faultstring></soap:Fault>"));

        StepVerifier.create(gateway.send(createTestRequest()))
            .expectErrorMatches(throwable -> throwable instanceof SoapCommunicationException)
            .verify();
    }

    @Test
    void send_shouldMapClientError_when4xx() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setBody("<soap:Fault>Bad Request</soap:Fault>"));

        StepVerifier.create(gateway.send(createTestRequest()))
            .expectErrorMatches(throwable ->
                throwable instanceof SoapCommunicationException &&
                ProcessingResultCodes.CLIENT_ERROR.equals(((SoapCommunicationException) throwable).getErrorCode()))
            .verify();
    }

    @Test
    void send_shouldIncludeErrorBodyInException() {
        String errorBody = "<soap:Fault><faultstring>Invalid document format</faultstring></soap:Fault>";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody(errorBody));

        StepVerifier.create(gateway.send(createTestRequest()))
            .expectErrorMatches(throwable -> {
                if (!(throwable instanceof SoapCommunicationException)) return false;
                String message = throwable.getMessage();
                return message.contains("Invalid document format");
            })
            .verify();
    }

    @Test
    void send_shouldReturnResult_whenBusinessFailure() {
        String responseXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:file=\"http://example.com/fileservice\">" +
            "<soap:Header/>" +
            "<soap:Body>" +
            "<file:UploadFileResponse>" +
            "<file:status>FAILURE</file:status>" +
            "<file:message>Business validation failed</file:message>" +
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

        StepVerifier.create(gateway.send(createTestRequest()))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Business validation failed", result.getMessage());
            })
            .verifyComplete();
    }

    @Test
    void send_shouldReturnError_withTraceIdInException() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<soap:Fault>Server Error</soap:Fault>"));

        StepVerifier.create(gateway.send(createTestRequest()))
            .expectErrorMatches(throwable -> {
                if (!(throwable instanceof SoapCommunicationException)) return false;
                SoapCommunicationException sce = (SoapCommunicationException) throwable;
                return "trace-123".equals(sce.getTraceId());
            })
            .verify();
    }

    @Test
    void send_shouldMap500ToBadGateway() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<soap:Fault>Internal Error</soap:Fault>"));

        StepVerifier.create(gateway.send(createTestRequest()))
            .expectErrorMatches(throwable -> {
                if (!(throwable instanceof SoapCommunicationException)) return false;
                return ProcessingResultCodes.BAD_GATEWAY.equals(((SoapCommunicationException) throwable).getErrorCode());
            })
            .verify();
    }
}
