package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentResultTest {

    @Test
    void builder_shouldCreateInstance() {
        java.time.Instant now = java.time.Instant.now();

        DocumentResult result = DocumentResult.builder()
            .status("SUCCESS")
            .message("Upload successful")
            .correlationId("corr-123")
            .traceId("trace-456")
            .processedAt(now)
            .externalReference("file-1")
            .success(true)
            .build();

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Upload successful", result.getMessage());
        assertEquals("corr-123", result.getCorrelationId());
        assertEquals("trace-456", result.getTraceId());
        assertEquals(now, result.getProcessedAt());
        assertEquals("file-1", result.getExternalReference());
        assertTrue(result.isSuccess());
    }

    @Test
    void builder_withFailure_shouldSetSuccessFalse() {
        DocumentResult result = DocumentResult.builder()
            .status("FAILURE")
            .message("Upload failed")
            .success(false)
            .build();

        assertEquals("FAILURE", result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    void getterAnnotations_shouldWork() {
        DocumentResult result = DocumentResult.builder()
            .status("SUCCESS")
            .build();

        assertEquals("SUCCESS", result.getStatus());
    }
}