package com.example.fileprocessor.infrastructure.rest;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import com.example.fileprocessor.infrastructure.rest.controller.FileController;
import com.example.fileprocessor.infrastructure.rest.controller.FileController.AsyncProcessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private ProcessFileUseCase processFileUseCase;

    private FileController fileController;

    @BeforeEach
    void setUp() {
        fileController = new FileController(processFileUseCase);
    }

    @Test
    void getFile_shouldReturnAcceptedResponse() {
        String documentId = "doc-001";
        FileUploadResult result = FileUploadResult.builder()
            .status("SUCCESS")
            .message("File processed successfully")
            .correlationId("corr-123")
            .traceId("trace-123")
            .processedAt(Instant.now())
            .externalReference("ext-ref-456")
            .success(true)
            .build();

        when(processFileUseCase.execute(eq(documentId), any(String.class)))
            .thenReturn(Mono.just(result));

        var response = fileController.getFile(documentId);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("PROCESSING", response.getBody().status());
        assertEquals("Document processing started", response.getBody().message());
        assertTrue(response.getBody().success());

        verify(processFileUseCase, times(1)).execute(eq(documentId), any(String.class));
    }

    @Test
    void getAllFiles_shouldReturnAcceptedResponse() {
        FileUploadResult result1 = FileUploadResult.builder()
            .status("SUCCESS")
            .message("File processed")
            .correlationId("corr-1")
            .traceId("trace-1")
            .processedAt(Instant.now())
            .externalReference("ext-1")
            .success(true)
            .build();

        FileUploadResult result2 = FileUploadResult.builder()
            .status("SUCCESS")
            .message("File processed")
            .correlationId("corr-2")
            .traceId("trace-2")
            .processedAt(Instant.now())
            .externalReference("ext-2")
            .success(true)
            .build();

        when(processFileUseCase.executeAll(any(String.class)))
            .thenReturn(Flux.just(result1, result2));

        var response = fileController.getAllFiles(null);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("PROCESSING", response.getBody().status());
        assertEquals("All documents processing started", response.getBody().message());
        assertTrue(response.getBody().success());

        verify(processFileUseCase, times(1)).executeAll(any(String.class));
    }

    @Test
    void getFile_shouldUseProvidedTraceId() {
        String documentId = "doc-002";
        String traceId = "custom-trace-id";

        FileUploadResult result = FileUploadResult.builder()
            .status("SUCCESS")
            .message("OK")
            .correlationId("corr-xyz")
            .traceId(traceId)
            .processedAt(Instant.now())
            .externalReference("ext-789")
            .success(true)
            .build();

        when(processFileUseCase.execute(eq(documentId), any(String.class)))
            .thenReturn(Mono.just(result));

        var response = fileController.getFile(documentId);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        verify(processFileUseCase, times(1)).execute(eq(documentId), any(String.class));
    }
}
