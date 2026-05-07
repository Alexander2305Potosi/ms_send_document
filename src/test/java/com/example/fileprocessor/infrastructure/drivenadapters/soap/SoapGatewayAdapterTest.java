package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.config.SoapV2Properties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.mapper.SoapV2Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapGatewayAdapterTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private SoapMapper soapMapper;

    @Mock
    private SoapV2Mapper soapV2Mapper;

    @Mock
    private WebClient.RequestBodyUriSpec bodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec headersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private SoapProperties soapProperties;
    private SoapV2Properties soapV2Properties;
    private SoapGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        soapProperties = new SoapProperties("http://localhost:8080/soap", 30, 0);

        soapV2Properties = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            null, null, null, null, null, null, null, null,
            30, 0
        );

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.clientConnector(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter = new SoapGatewayAdapter(webClientBuilder, soapProperties, soapV2Properties,
                soapMapper, soapV2Mapper);
    }

    private void mockWebClientSuccessV1(String responseBody) {
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    private void mockWebClientErrorV1(Throwable error) {
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(error));
    }

    private void mockWebClientSuccessV2(String responseBody) {
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    private void mockWebClientErrorV2(Throwable error) {
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(error));
    }

    private static FileUploadRequest request() {
        return FileUploadRequest.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .contentType("application/pdf")
            .content(new byte[]{1, 2, 3})
            .origin("test-origin")
            .build();
    }

    private static ExternalServiceResponse successResponse() {
        return ExternalServiceResponse.builder()
            .status("OK")
            .message("Uploaded successfully")
            .correlationId("corr-123")
            .processedAt(Instant.now())
            .build();
    }

    // isRetryable tests

    @Test
    void isRetryable_with503_returnsTrue() {
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        assertTrue(adapter.isRetryable(ex));
    }

    @Test
    void isRetryable_with429_returnsTrue() {
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        assertTrue(adapter.isRetryable(ex));
    }

    @Test
    void isRetryable_with500_returnsFalse() {
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        assertFalse(adapter.isRetryable(ex));
    }

    @Test
    void isRetryable_with400_returnsFalse() {
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertFalse(adapter.isRetryable(ex));
    }

    @Test
    void isRetryable_withTimeoutException_returnsTrue() {
        assertTrue(adapter.isRetryable(new TimeoutException()));
    }

    @Test
    void isRetryable_withConnectException_returnsTrue() {
        assertTrue(adapter.isRetryable(new ConnectException("Connection refused")));
    }

    @Test
    void isRetryable_withOtherException_returnsFalse() {
        assertFalse(adapter.isRetryable(new RuntimeException()));
    }

    // buildErrorResult tests

    @Test
    void buildErrorResult_createsFailureResult() {
        FileUploadResult result = adapter.buildErrorResult("trace-1", SoapErrorCodes.GATEWAY_TIMEOUT, "timeout", 3);

        assertFalse(result.isSuccess());
        assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
        assertEquals(SoapErrorCodes.GATEWAY_TIMEOUT, result.getErrorCode());
        assertEquals("trace-1", result.getTraceId());
        assertEquals("timeout", result.getMessage());
        assertEquals(3, result.getAttemptCount());
    }

    // toFileUploadResult tests

    @Test
    void toFileUploadResult_withSuccessResponse_mapsFields() {
        ExternalServiceResponse response = successResponse();

        FileUploadResult result = adapter.toFileUploadResult(response, 2);

        assertTrue(result.isSuccess());
        assertEquals("OK", result.getStatus());
        assertEquals("corr-123", result.getCorrelationId());
        assertEquals(2, result.getAttemptCount());
    }

    @Test
    void toFileUploadResult_withFailureResponse_mapsFailure() {
        ExternalServiceResponse response = ExternalServiceResponse.builder()
            .status("ERROR")
            .message("Something went wrong")
            .build();

        FileUploadResult result = adapter.toFileUploadResult(response, 1);

        assertFalse(result.isSuccess());
        assertEquals("ERROR", result.getStatus());
    }

    // send (V1) tests

    @Test
    void send_whenSuccessful_returnsSuccessResult() {
        when(soapMapper.toFullSoapMessage(any())).thenReturn("<soap>body</soap>");
        when(soapMapper.fromSoapXml("<response>ok</response>")).thenReturn(successResponse());
        mockWebClientSuccessV1("<response>ok</response>");

        StepVerifier.create(adapter.send(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void send_whenWebClientError500_returnsBadGateway() {
        when(soapMapper.toFullSoapMessage(any())).thenReturn("<soap>body</soap>");
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        when(ex.getMessage()).thenReturn("Server error");
        mockWebClientErrorV1(ex);

        StepVerifier.create(adapter.send(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
                assertEquals(SoapErrorCodes.BAD_GATEWAY, result.getErrorCode());
            })
            .verifyComplete();
    }

    @Test
    void send_whenIOException_returnsUnknownError() {
        when(soapMapper.toFullSoapMessage(any())).thenReturn("<soap>body</soap>");
        mockWebClientErrorV1(new IOException("IO error"));

        StepVerifier.create(adapter.send(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(SoapErrorCodes.UNKNOWN_ERROR, result.getErrorCode());
            })
            .verifyComplete();
    }

    @Test
    void send_whenUnexpectedThrowable_returnsUnknownError() {
        when(soapMapper.toFullSoapMessage(any())).thenReturn("<soap>body</soap>");
        mockWebClientErrorV1(new RuntimeException("unexpected"));

        StepVerifier.create(adapter.send(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(SoapErrorCodes.UNKNOWN_ERROR, result.getErrorCode());
            })
            .verifyComplete();
    }

    // send_withoutTraceIdContext uses "unknown" default

    @Test
    void send_whenNoTraceIdInContext_usesDefault() {
        when(soapMapper.toFullSoapMessage(any())).thenReturn("<soap>body</soap>");
        when(soapMapper.fromSoapXml(any())).thenReturn(successResponse());
        mockWebClientSuccessV1("<response>ok</response>");

        StepVerifier.create(adapter.send(request()))
            .assertNext(result -> assertTrue(result.isSuccess()))
            .verifyComplete();
    }

    // transmitirDocumento (V2) tests

    @Test
    void transmitirDocumento_whenSuccessful_returnsSuccessResult() {
        when(soapV2Mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soapenv:Envelope>...</soapenv:Envelope>");
        when(soapV2Mapper.parseResponse("<response>ok</response>", "trace-v2-1")).thenReturn(successResponse());
        mockWebClientSuccessV2("<response>ok</response>");

        StepVerifier.create(adapter.transmitirDocumento(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-v2-1")))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void transmitirDocumento_whenHttp500_returnsBadGateway() {
        when(soapV2Mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soapenv:Envelope>...</soapenv:Envelope>");
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        when(ex.getMessage()).thenReturn("Server error");
        mockWebClientErrorV2(ex);

        StepVerifier.create(adapter.transmitirDocumento(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-v2-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(SoapErrorCodes.BAD_GATEWAY, result.getErrorCode());
            })
            .verifyComplete();
    }

    @Test
    void transmitirDocumento_whenIOException_returnsUnknownError() {
        when(soapV2Mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soapenv:Envelope>...</soapenv:Envelope>");
        mockWebClientErrorV2(new IOException("IO error"));

        StepVerifier.create(adapter.transmitirDocumento(request())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-v2-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(SoapErrorCodes.UNKNOWN_ERROR, result.getErrorCode());
            })
            .verifyComplete();
    }

    @Test
    void transmitirDocumento_whenNoTraceIdInContext_usesDefault() {
        when(soapV2Mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soapenv:Envelope>...</soapenv:Envelope>");
        when(soapV2Mapper.parseResponse(any(), anyString())).thenReturn(successResponse());
        mockWebClientSuccessV2("<response>ok</response>");

        StepVerifier.create(adapter.transmitirDocumento(request()))
            .assertNext(result -> assertTrue(result.isSuccess()))
            .verifyComplete();
    }
}
