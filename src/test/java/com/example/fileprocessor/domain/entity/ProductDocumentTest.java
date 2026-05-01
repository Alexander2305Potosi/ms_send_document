package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductDocumentTest {

    @Test
    void productDocument_record_createsValidDocument() {
        byte[] content = new byte[]{1, 2, 3};
        ProductDocument doc = new ProductDocument(
            "doc-1",
            "test.pdf",
            content,
            "application/pdf",
            1024L,
            true,
            "origin"
        );

        assertEquals("doc-1", doc.documentId());
        assertEquals("test.pdf", doc.filename());
        assertArrayEquals(content, doc.content());
        assertEquals("application/pdf", doc.contentType());
        assertEquals(1024L, doc.size());
        assertTrue(doc.isZip());
        assertEquals("origin", doc.origin());
    }

    @Test
    void size_returnsCorrectValue() {
        ProductDocument doc = new ProductDocument(
            "doc-1",
            "test.pdf",
            new byte[100],
            "application/pdf",
            100L,
            false,
            "origin"
        );

        assertEquals(100L, doc.size());
    }

    @Test
    void isZip_whenFalse() {
        ProductDocument doc = new ProductDocument(
            "doc-1",
            "test.pdf",
            new byte[0],
            "application/pdf",
            0L,
            false,
            "origin"
        );

        assertFalse(doc.isZip());
    }
}
