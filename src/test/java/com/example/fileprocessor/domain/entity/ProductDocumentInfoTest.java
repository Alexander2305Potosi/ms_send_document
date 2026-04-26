package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductDocumentInfoTest {

    @Test
    void record_shouldCreateInstance() {
        ProductDocumentInfo doc = new ProductDocumentInfo(
            "doc-1",
            "test.pdf",
            new byte[]{1, 2, 3},
            "application/pdf",
            3,
            false,
            "incoming/docs"
        );

        assertEquals("doc-1", doc.documentId());
        assertEquals("test.pdf", doc.filename());
        assertArrayEquals(new byte[]{1, 2, 3}, doc.content());
        assertEquals("application/pdf", doc.contentType());
        assertEquals(3, doc.size());
        assertFalse(doc.isZip());
        assertEquals("incoming/docs", doc.origin());
    }

    @Test
    void extension_shouldReturnPdf() {
        ProductDocumentInfo doc = new ProductDocumentInfo(
            "doc-1", "document.pdf", null, null, 0, false, null
        );

        assertEquals("pdf", doc.extension());
    }

    @Test
    void extension_shouldReturnEmptyForNoExtension() {
        ProductDocumentInfo doc = new ProductDocumentInfo(
            "doc-1", "noextension", null, null, 0, false, null
        );

        assertEquals("", doc.extension());
    }

    @Test
    void isZipArchive_shouldReturnTrueWhenIsZipFlagTrue() {
        ProductDocumentInfo doc = new ProductDocumentInfo(
            "doc-1", "document.pdf", null, null, 0, true, null
        );

        assertTrue(doc.isZipArchive());
    }

    @Test
    void isZipArchive_shouldReturnTrueForZipExtension() {
        ProductDocumentInfo doc = new ProductDocumentInfo(
            "doc-1", "archive.zip", null, null, 0, false, null
        );

        assertTrue(doc.isZipArchive());
    }

    @Test
    void isZipArchive_shouldReturnFalseForNonZip() {
        ProductDocumentInfo doc = new ProductDocumentInfo(
            "doc-1", "document.pdf", null, null, 0, false, null
        );

        assertFalse(doc.isZipArchive());
    }
}