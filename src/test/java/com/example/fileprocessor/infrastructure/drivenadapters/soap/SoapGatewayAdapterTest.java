package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapGatewayAdapterTest {

    @Mock
    private WebClient soapWebClient;

    @Mock
    private SoapMapper mapper;

    @Mock
    private WebClient.RequestBodyUriSpec bodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec headersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private SoapProperties properties;
    private SoapGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new SoapProperties(
            "http://localhost:8080/soap", "SYS-01", "user", "h-ns", "b-ns", "s-ns",
            "token", "dest-name", "dest-ns", "dest-op", "action", "CLASS-1", 
            Map.of(), Map.of(), 5, 1 // Short timeout and 1 retry
        );
        adapter = new SoapGatewayAdapter(soapWebClient, properties, mapper);
    }

    private void mockWebClientSuccess(String responseBody) {
        when(soapWebClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        doReturn(headersSpec).when(bodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    private void mockWebClientError(Throwable error) {
        when(soapWebClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        doReturn(headersSpec).when(bodyUriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(error));
    }

    @Test
    void send_whenSuccessful_returnsSuccessResult() {
        when(mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soap>request</soap>");
        when(mapper.parseResponse(anyString(), anyString())).thenReturn(
            FileUploadResponse.builder()
                .status("OK")
                .success(true)
                .correlationId("corr-123")
                .processedAt(Instant.now())
                .build()
        );
        mockWebClientSuccess("<soap>response</soap>");

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
        when(mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soap>request</soap>");
        mockWebClientError(new TimeoutException("Read timeout"));

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), result.getErrorCode());
                assertTrue(result.getMessage().contains("Timeout"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenHttp500WithSoapFault_parsesFaultFromBody() {
        when(mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soap>request</soap>");
        
        WebClientResponseException ex = WebClientResponseException.create(
            500, "Internal Server Error", null, "<Fault>Error</Fault>".getBytes(), null
        );
        mockWebClientError(ex);
        
        when(mapper.parseResponse(eq("<Fault>Error</Fault>"), anyString())).thenReturn(
            FileUploadResponse.builder()
                .status("FAILURE")
                .success(false)
                .message("Parsed Error")
                .build()
        );

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Parsed Error", result.getMessage());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenGenericError_returnsUnknownError() {
        when(mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soap>request</soap>");
        mockWebClientError(new RuntimeException("Fatal failure"));

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getErrorCode());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }
}
