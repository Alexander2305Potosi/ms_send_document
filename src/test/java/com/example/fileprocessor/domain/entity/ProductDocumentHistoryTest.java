package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductDocumentHistoryTest {

    @Test
    void productDocument_builder_createsValidDocument() {
        byte[] content = new byte[]{1, 2, 3};
        ProductDocumentHistory doc = ProductDocumentHistory.builder()
            .productId("prod-1")
            .isZip(true)
            .pais("AR")
            .documentId("doc-1")
            .name("test.pdf")
            .filename("test.pdf")
            .contentType("application/pdf")
            .size(1024L)
            .origin("origin")
            .content(content)
            .build();

        assertEquals("prod-1", doc.getProductId());
        assertEquals("doc-1", doc.getDocumentId());
        assertEquals("test.pdf", doc.getFilename());
        assertArrayEquals(content, doc.getContent());
        assertEquals("application/pdf", doc.getContentType());
        assertEquals(1024L, doc.getSize());
        assertTrue(doc.isZip());
        assertEquals("origin", doc.getOrigin());
        assertEquals("AR", doc.getPais());
    }

    @Test
    void size_returnsCorrectValue() {
        ProductDocumentHistory doc = ProductDocumentHistory.builder()
            .productId("prod-1")
            .isZip(false)
            .pais("AR")
            .documentId("doc-1")
            .filename("test.pdf")
            .size(100L)
            .origin("origin")
            .content(new byte[100])
            .build();

        assertEquals(100L, doc.getSize());
    }

    @Test
    void isZip_whenFalse() {
        ProductDocumentHistory doc = ProductDocumentHistory.builder()
            .productId("prod-1")
            .isZip(false)
            .pais("AR")
            .documentId("doc-1")
            .filename("test.pdf")
            .size(0L)
            .origin("origin")
            .content(new byte[0])
            .build();

        assertFalse(doc.isZip());
    }
}