package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileDataTest {

    @Test
    void builder_shouldCreateInstance() {
        FileData fileData = FileData.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .size(3)
            .contentType("application/pdf")
            .traceId("trace-1")
            .build();

        assertEquals("doc-1", fileData.getDocumentId());
        assertEquals("test.pdf", fileData.getFilename());
        assertArrayEquals(new byte[]{1, 2, 3}, fileData.getContent());
        assertEquals(3, fileData.getSize());
        assertEquals("application/pdf", fileData.getContentType());
        assertEquals("trace-1", fileData.getTraceId());
    }

    @Test
    void extension_shouldReturnPdf() {
        FileData fileData = FileData.builder()
            .filename("document.pdf")
            .build();

        assertEquals("pdf", fileData.extension());
    }

    @Test
    void extension_shouldReturnUppercaseAsLowercase() {
        FileData fileData = FileData.builder()
            .filename("document.PDF")
            .build();

        assertEquals("pdf", fileData.extension());
    }

    @Test
    void extension_shouldReturnEmptyForNoDot() {
        FileData fileData = FileData.builder()
            .filename("noextension")
            .build();

        assertEquals("", fileData.extension());
    }

    @Test
    void extension_shouldHandleMultipleDots() {
        FileData fileData = FileData.builder()
            .filename("file.tar.gz")
            .build();

        assertEquals("gz", fileData.extension());
    }
}