package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapGatewayAdapterTest {

    private MockWebServer mockWebServer;

    @Mock
    private SoapMapper mapper;

    private SoapProperties properties;
    private SoapGatewayAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new SoapProperties(
            "http://127.0.0.1:" + mockWebServer.getPort() + "/soap", "SYS-01", "user", "h-ns", "b-ns", "s-ns",
            "token", "dest-name", "dest-ns", "dest-op", "action", "CLASS-1", 
            Map.of(), Map.of(), 2, 0 // 2 seconds timeout and NO retries
        );
        adapter = new SoapGatewayAdapter(WebClient.builder(), properties, mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void send_whenSuccessful_returnsSuccessResult() {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        when(mapper.parseResponse(anyString(), anyString())).thenReturn(
            FileUploadResponse.builder()
                .status("OK")
                .success(true)
                .correlationId("corr-123")
                .processedAt(Instant.now())
                .build()
        );

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("<soap>response</soap>")
            .addHeader("Content-Type", "text/xml"));

        StepVerifier.create(adapter.send(FileUploadRequest.builder()
                .filename("test.pdf")
                .content(new byte[]{1})
                .build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenTimeout_returnsGatewayTimeout() {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        
        mockWebServer.enqueue(new MockResponse()
            .setHeadersDelay(4, TimeUnit.SECONDS) // Delay exceeds 2 seconds timeout
            .setBody("<soap>response</soap>"));

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .thenConsumeWhile(FileUploadResponse::isTechnicalRetry)
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), result.getSyncStatus());
                assertTrue(result.getMessage().contains("Timeout"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenHttp500WithSoapFault_parsesFaultFromBody() {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<Fault>Error</Fault>")
            .addHeader("Content-Type", "text/xml"));
        
        when(mapper.parseResponse(eq("<Fault>Error</Fault>"), anyString())).thenReturn(
            FileUploadResponse.builder()
                .status("FAILURE")
                .success(false)
                .message("Parsed Error")
                .build()
        );

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .thenConsumeWhile(FileUploadResponse::isTechnicalRetry)
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Parsed Error", result.getMessage());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenGenericError_returnsUnknownError() {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        
        mockWebServer.enqueue(new MockResponse()
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .thenConsumeWhile(FileUploadResponse::isTechnicalRetry)
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }
}
