package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductToProcessTest {

    @Test
    void builder_shouldCreateInstance() {
        java.time.Instant now = java.time.Instant.now();

        ProductToProcess product = ProductToProcess.builder()
            .productId("prod-1")
            .name("Test Product")
            .status("PENDING")
            .createdAt(now)
            .processedAt(now)
            .traceId("trace-1")
            .build();

        assertEquals("prod-1", product.getProductId());
        assertEquals("Test Product", product.getName());
        assertEquals("PENDING", product.getStatus());
        assertEquals(now, product.getCreatedAt());
        assertEquals(now, product.getProcessedAt());
        assertEquals("trace-1", product.getTraceId());
    }

    @Test
    void builder_withMinimalFields_shouldCreateInstance() {
        ProductToProcess product = ProductToProcess.builder()
            .productId("prod-2")
            .name("Minimal Product")
            .build();

        assertEquals("prod-2", product.getProductId());
        assertEquals("Minimal Product", product.getName());
        assertNull(product.getStatus());
    }
}