package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductDocumentHistoryTest {

    @Test
    void productDocument_record_createsValidDocument() {
        byte[] content = new byte[]{1, 2, 3};
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1",
            "test.pdf",
            content,
            "application/pdf",
            1024L,
            true,
            "origin",
            "AR"
        );

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
            "doc-1",
            "test.pdf",
            new byte[100],
            "application/pdf",
            100L,
            false,
            "origin",
            "AR"
        );

        assertEquals(100L, doc.size());
    }

    @Test
    void isZip_whenFalse() {
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1",
            "test.pdf",
            new byte[0],
            "application/pdf",
            0L,
            false,
            "origin",
            "AR"
        );

        assertFalse(doc.isZip());
    }
}