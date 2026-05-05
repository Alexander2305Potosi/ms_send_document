package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductDocumentHistoryTest {

    @Test
    void productDocument_record_createsValidDocument() {
        byte[] content = new byte[]{1, 2, 3};
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "prod-1", true, "AR",
            null, "doc-1", null, null,
            "test.pdf", "owner", null,
            "PENDING", null, "SYNCED", null,
            "test.pdf", "application/pdf", 1024L, "origin",
            content, null
        );

        assertEquals("prod-1", doc.productId());
        assertEquals("doc-1", doc.documentId());
        assertEquals("test.pdf", doc.filename());
        assertArrayEquals(content, doc.content());
        assertEquals("application/pdf", doc.contentType());
        assertEquals(1024L, doc.size());
        assertTrue(doc.isZip());
        assertEquals("origin", doc.origin());
        assertEquals("AR", doc.pais());
    }

    @Test
    void size_returnsCorrectValue() {
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "prod-1", false, "AR",
            null, "doc-1", null, null,
            "test.pdf", "owner", null,
            "PENDING", null, "SYNCED", null,
            "test.pdf", "application/pdf", 100L, "origin",
            new byte[100], null
        );

        assertEquals(100L, doc.size());
    }

    @Test
    void isZip_whenFalse() {
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "prod-1", false, "AR",
            null, "doc-1", null, null,
            "test.pdf", "owner", null,
            "PENDING", null, "SYNCED", null,
            "test.pdf", "application/pdf", 0L, "origin",
            new byte[0], null
        );

        assertFalse(doc.isZip());
    }
}