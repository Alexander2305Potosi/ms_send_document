package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadProductsResultTest {

    @Test
    void builder_shouldCreateInstance() {
        java.time.Instant now = java.time.Instant.now();

        LoadProductsResult result = LoadProductsResult.builder()
            .productId("prod-1")
            .name("Test Product")
            .documentCount(5)
            .status("PENDING")
            .message("Products loaded")
            .traceId("trace-123")
            .processedAt(now)
            .success(true)
            .build();

        assertEquals("prod-1", result.getProductId());
        assertEquals("Test Product", result.getName());
        assertEquals(5, result.getDocumentCount());
        assertEquals("PENDING", result.getStatus());
        assertEquals("Products loaded", result.getMessage());
        assertEquals("trace-123", result.getTraceId());
        assertEquals(now, result.getProcessedAt());
        assertTrue(result.isSuccess());
    }

    @Test
    void builder_withFailure_shouldSetSuccessFalse() {
        LoadProductsResult result = LoadProductsResult.builder()
            .productId("prod-2")
            .status("FAILURE")
            .message("Failed to load")
            .success(false)
            .build();

        assertFalse(result.isSuccess());
    }
}