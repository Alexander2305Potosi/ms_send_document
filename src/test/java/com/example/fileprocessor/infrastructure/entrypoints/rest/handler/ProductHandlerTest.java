package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductHandlerTest {

    @Mock
    private SoapDocumentProcessingUseCase soapDocumentUseCase;

    @Mock
    private ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;

    @Mock
    private S3DocumentProcessingUseCase s3DocumentUseCase;

    @Mock
    private SyncDocumentsUseCase syncDocumentsUseCase;

    private ProductHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProductHandler(soapDocumentUseCase, s3DocumentUseCaseProvider, syncDocumentsUseCase, null, null);
    }

    private static ServerRequest mockRequestForProcessing(String processorParam, String traceIdHeader) {
        ServerRequest request = mock(ServerRequest.class);
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);

        lenient().when(request.queryParam("processor"))
            .thenReturn(processorParam != null ? Optional.of(processorParam) : Optional.empty());
        when(request.headers()).thenReturn(headers);

        org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
        if (traceIdHeader != null) {
            httpHeaders.add(ApiConstants.HEADER_TRACE_ID, traceIdHeader);
        }
        httpHeaders.add(ApiConstants.HEADER_USE_CASE, "default");
        when(headers.asHttpHeaders()).thenReturn(httpHeaders);

        java.util.Map<String, String> pathVars = new java.util.HashMap<>();
        String expectedProcessor = (processorParam != null) ? processorParam : ApiConstants.PROCESSOR_SOAP;
        pathVars.put(ApiConstants.TYPE_JOB, expectedProcessor);
        when(request.pathVariables()).thenReturn(pathVars);
        lenient().when(request.pathVariable(ApiConstants.TYPE_JOB)).thenReturn(expectedProcessor);

        return request;
    }

    private static ServerRequest mockRequestForSync(String traceIdHeader) {
        ServerRequest request = mock(ServerRequest.class);
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        
        org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
        if (traceIdHeader != null) {
            httpHeaders.add(ApiConstants.HEADER_TRACE_ID, traceIdHeader);
        }
        httpHeaders.add(ApiConstants.HEADER_USE_CASE, "retention");
        when(headers.asHttpHeaders()).thenReturn(httpHeaders);

        lenient().when(request.queryParam(anyString())).thenReturn(Optional.empty());
        lenient().when(request.pathVariables()).thenReturn(java.util.Map.of(ApiConstants.TYPE_JOB, "retention"));
        lenient().when(request.pathVariable(anyString())).thenReturn("retention");
        
        return request;
    }

    private static FileUploadResponse successResult() {
        return FileUploadResponse.builder()
            .status(ProcessingResultCodes.SUCCESS.name())
            .success(true)
            .correlationId("corr-123")
            .traceId("trace-1")
            .processedAt(Instant.now())
            .build();
    }

    // getProcessor tests

    @Test
    void getProcessor_withSoap_returnsSoapUseCase() {
        assertSame(soapDocumentUseCase, handler.getProcessor("soap"));
    }

    @Test
    void getProcessor_withS3Available_returnsS3UseCase() {
        when(s3DocumentUseCaseProvider.getIfAvailable()).thenReturn(s3DocumentUseCase);
        assertSame(s3DocumentUseCase, handler.getProcessor("s3"));
    }

    @Test
    void getProcessor_withS3NotAvailable_throws503() {
        when(s3DocumentUseCaseProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> handler.getProcessor("s3"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("enable 's3' profile"));
    }

    @Test
    void getProcessor_withUnknownType_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> handler.getProcessor("ftp"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Unknown processor type"));
    }

    // processPendingProducts tests

    @Test
    void processPendingProducts_defaultsToSoap_returnsAccepted() {
        ServerRequest request = mockRequestForProcessing(null, "trace-1");
        when(soapDocumentUseCase.executePendingDocuments()).thenReturn(Flux.just(successResult()));

        Mono<ServerResponse> responseMono = handler.processPendingProducts(request);

        StepVerifier.create(responseMono)
            .assertNext(response -> {
                assertEquals(HttpStatus.ACCEPTED, response.statusCode());
                assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
            })
            .verifyComplete();
    }

    @Test
    void processPendingProducts_withS3Param_returnsAccepted() {
        ServerRequest request = mockRequestForProcessing("s3", "trace-1");
        when(s3DocumentUseCaseProvider.getIfAvailable()).thenReturn(s3DocumentUseCase);
        when(s3DocumentUseCase.executePendingDocuments()).thenReturn(Flux.just(successResult()));

        Mono<ServerResponse> responseMono = handler.processPendingProducts(request);

        StepVerifier.create(responseMono)
            .assertNext(response -> {
                assertEquals(HttpStatus.ACCEPTED, response.statusCode());
                assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
            })
            .verifyComplete();
    }

    // syncProducts tests

    @Test
    void syncProducts_returnsImmediateOk() {
        ServerRequest request = mockRequestForSync("trace-1");
        when(syncDocumentsUseCase.execute("retention")).thenReturn(Mono.just("completed"));

        Mono<ServerResponse> responseMono = handler.syncProducts(request);

        StepVerifier.create(responseMono)
            .assertNext(response -> {
                assertEquals(HttpStatus.ACCEPTED, response.statusCode());
                assertEquals(MediaType.TEXT_PLAIN, response.headers().getContentType());
            })
            .verifyComplete();

        verify(syncDocumentsUseCase).execute("retention");
    }

    @Test
    void syncProducts_whenExecuteFails_stillReturnsOk() {
        ServerRequest request = mockRequestForSync("trace-1");
        when(syncDocumentsUseCase.execute("retention")).thenReturn(Mono.error(new RuntimeException("fail")));

        Mono<ServerResponse> responseMono = handler.syncProducts(request);

        StepVerifier.create(responseMono)
            .assertNext(response -> assertEquals(HttpStatus.ACCEPTED, response.statusCode()))
            .verifyComplete();
    }
}
