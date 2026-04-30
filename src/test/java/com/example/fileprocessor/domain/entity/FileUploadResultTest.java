package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadResultTest {

    @Test
    void builder_shouldCreateInstance() {
        Instant now = Instant.now();

        FileUploadResult result = FileUploadResult.builder()
            .status("SUCCESS")
            .message("Upload completed")
            .correlationId("corr-123")
            .traceId("trace-456")
            .processedAt(now)
            .externalReference("doc-1")
            .success(true)
            .build();

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Upload completed", result.getMessage());
        assertEquals("corr-123", result.getCorrelationId());
        assertEquals("trace-456", result.getTraceId());
        assertEquals(now, result.getProcessedAt());
        assertEquals("doc-1", result.getExternalReference());
        assertTrue(result.isSuccess());
    }

    @Test
    void builder_withFailure_shouldSetSuccessFalse() {
        FileUploadResult result = FileUploadResult.builder()
            .status("FAILURE")
            .message("Upload failed")
            .success(false)
            .build();

        assertEquals("FAILURE", result.getStatus());
        assertFalse(result.isSuccess());
    }
}