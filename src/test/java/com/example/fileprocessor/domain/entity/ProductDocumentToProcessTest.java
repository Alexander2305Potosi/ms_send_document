package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductDocumentToProcessTest {

    @Test
    void builder_shouldCreateInstance() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc-1")
            .productId("prod-1")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .origin("incoming/docs")
            .status("PENDING")
            .build();

        assertEquals("doc-1", doc.getDocumentId());
        assertEquals("prod-1", doc.getProductId());
        assertEquals("test.pdf", doc.getFilename());
        assertArrayEquals(new byte[]{1, 2, 3}, doc.getContent());
        assertEquals("application/pdf", doc.getContentType());
        assertEquals("incoming/docs", doc.getOrigin());
        assertEquals("PENDING", doc.getStatus());
    }

    @Test
    void builder_withAllFields_shouldCreateInstance() {
        java.time.Instant now = java.time.Instant.now();

        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc-2")
            .productId("prod-2")
            .parentDocumentId("parent-1")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .origin("incoming/docs")
            .status("SUCCESS")
            .createdAt(now)
            .processedAt(now)
            .traceId("trace-1")
            .soapCorrelationId("corr-1")
            .errorCode("ERR_001")
            .build();

        assertEquals("doc-2", doc.getDocumentId());
        assertEquals("parent-1", doc.getParentDocumentId());
        assertEquals(now, doc.getCreatedAt());
        assertEquals(now, doc.getProcessedAt());
        assertEquals("trace-1", doc.getTraceId());
        assertEquals("corr-1", doc.getSoapCorrelationId());
        assertEquals("ERR_001", doc.getErrorCode());
    }

    @Test
    void getterAnnotations_shouldWork() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc-3")
            .productId("prod-3")
            .build();

        assertEquals("doc-3", doc.getDocumentId());
        assertEquals("prod-3", doc.getProductId());
    }
}