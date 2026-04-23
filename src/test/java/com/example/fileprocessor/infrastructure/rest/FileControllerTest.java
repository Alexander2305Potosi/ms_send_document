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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    void processPendingDocuments_shouldReturnAcceptedResponse() {
        FileUploadResult result = FileUploadResult.builder()
            .status("SUCCESS")
            .message("File processed")
            .correlationId("corr-123")
            .traceId("trace-123")
            .processedAt(Instant.now())
            .externalReference("ext-1")
            .success(true)
            .build();

        when(processFileUseCase.executePendingDocuments()).thenReturn(Flux.just(result));

        var response = fileController.processPendingDocuments();

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("PROCESSING", response.getBody().status());
        assertEquals("Pending documents processing started", response.getBody().message());
        assertTrue(response.getBody().success());

        verify(processFileUseCase, times(1)).executePendingDocuments();
    }

    @Test
    void processPendingDocuments_shouldReturnAccepted_whenNoPendingDocuments() {
        when(processFileUseCase.executePendingDocuments()).thenReturn(Flux.empty());

        var response = fileController.processPendingDocuments();

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("PROCESSING", response.getBody().status());
        assertTrue(response.getBody().success());

        verify(processFileUseCase, times(1)).executePendingDocuments();
    }
}
