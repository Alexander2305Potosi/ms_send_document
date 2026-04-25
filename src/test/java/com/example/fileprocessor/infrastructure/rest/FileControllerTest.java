package com.example.fileprocessor.infrastructure.rest;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.usecase.LoadDocumentsResult;
import com.example.fileprocessor.domain.usecase.LoadDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import com.example.fileprocessor.infrastructure.rest.controller.FileController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private ProcessFileUseCase processFileUseCase;

    @Mock
    private LoadDocumentsUseCase loadDocumentsUseCase;

    private FileController fileController;

    @BeforeEach
    void setUp() {
        fileController = new FileController(processFileUseCase, loadDocumentsUseCase);
    }

    @Test
    void loadDocuments_shouldReturnAcceptedResponse() {
        LoadDocumentsResult result = LoadDocumentsResult.builder()
            .documentId("doc-001")
            .filename("test.pdf")
            .status("LOADED")
            .message("Document saved for processing")
            .traceId("trace-123")
            .processedAt(Instant.now())
            .success(true)
            .build();

        when(loadDocumentsUseCase.execute()).thenReturn(Flux.just(result));

        var response = fileController.loadDocuments();

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("LOADING", response.getBody().status());
        assertEquals("Document loading from REST API started", response.getBody().message());
        assertTrue(response.getBody().success());

        verify(loadDocumentsUseCase, times(1)).execute();
    }

    @Test
    void loadDocuments_shouldReturnAccepted_whenNoDocuments() {
        when(loadDocumentsUseCase.execute()).thenReturn(Flux.empty());

        var response = fileController.loadDocuments();

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("LOADING", response.getBody().status());
        assertTrue(response.getBody().success());

        verify(loadDocumentsUseCase, times(1)).execute();
    }

    @Test
    void processPendingDocuments_shouldReturnAcceptedResponse() {
        var result = com.example.fileprocessor.domain.entity.FileUploadResult.builder()
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
        assertEquals(DocumentStatus.PROCESSING_VALUE, response.getBody().status());
        assertEquals("Pending documents processing started", response.getBody().message());
        assertTrue(response.getBody().success());

        verify(processFileUseCase, times(1)).executePendingDocuments();
    }
}
