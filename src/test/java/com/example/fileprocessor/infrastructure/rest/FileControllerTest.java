package com.example.fileprocessor.infrastructure.rest;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import com.example.fileprocessor.infrastructure.config.FileUploadProperties;
import com.example.fileprocessor.infrastructure.rest.controller.FileController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private ProcessFileUseCase processFileUseCase;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private FilePart filePart;

    private FileController fileController;

    @BeforeEach
    void setUp() {
        FileUploadProperties properties = new FileUploadProperties(
            10 * 1024 * 1024, "pdf,docx,txt", 255);
        fileController = new FileController(processFileUseCase, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadFile_shouldReturnSuccessResponse() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        String filename = "test.pdf";
        String traceId = "test-trace-id";

        FileData fileData = new FileData(content, filename, content.length,
            "application/pdf", traceId);
        FileUploadResult result = new FileUploadResult("SUCCESS", "File processed",
            "corr-123", traceId, java.time.Instant.now(), "ext-ref", true);

        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(content);

        // Setup FilePart mock
        when(filePart.content()).thenReturn(Flux.just(dataBuffer));
        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(new org.springframework.http.HttpHeaders());

        // Setup multipart data
        MultiValueMap<String, Part> multipartData = mock(MultiValueMap.class);
        when(multipartData.getFirst("file")).thenReturn(filePart);

        // Setup request mock
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        when(request.getHeaders()).thenReturn(headers);

        // Setup exchange mock
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getMultipartData()).thenReturn(Mono.just(multipartData));

        // Setup usecase
        when(processFileUseCase.execute(any(FileData.class))).thenReturn(Mono.just(result));

        StepVerifier.create(fileController.uploadFile(exchange))
            .assertNext(response -> {
                assert response.getStatusCode().is2xxSuccessful();
            })
            .verifyComplete();

        verify(processFileUseCase, times(1)).execute(any(FileData.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadFile_shouldReturnError_whenNoFileProvided() {
        // Setup multipart data with no file
        MultiValueMap<String, Part> multipartData = mock(MultiValueMap.class);
        when(multipartData.getFirst("file")).thenReturn(null);

        // Setup request mock
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        // Setup exchange mock
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getMultipartData()).thenReturn(Mono.just(multipartData));

        StepVerifier.create(fileController.uploadFile(exchange))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
}
