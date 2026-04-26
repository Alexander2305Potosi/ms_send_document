package com.example.fileprocessor.infrastructure.helpers.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadPropertiesTest {

    @Test
    void record_shouldCreateInstance() {
        FileUploadProperties props = new FileUploadProperties(
            1024L,
            "application/pdf",
            255,
            List.of("tmp"),
            50,
            List.of("keyword"),
            List.of("pattern")
        );

        assertEquals(1024L, props.maxSize());
        assertEquals("application/pdf", props.allowedTypes());
        assertEquals(255, props.maxFilenameLength());
        assertEquals(List.of("tmp"), props.foldersToSkip());
        assertEquals(50, props.maxFileSizeMb());
        assertEquals(List.of("keyword"), props.keywords());
        assertEquals(List.of("pattern"), props.originPatternsToSend());
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        FileUploadProperties props = new FileUploadProperties(
            2048L,
            "text/plain",
            128,
            List.of("skip1", "skip2"),
            25,
            List.of("kw1"),
            List.of("origin1", "origin2")
        );

        assertEquals(2048L, props.maxSize());
        assertEquals("text/plain", props.allowedTypes());
        assertEquals(128, props.maxFilenameLength());
        assertEquals(2, props.foldersToSkip().size());
        assertEquals(25, props.maxFileSizeMb());
    }

    @Test
    void equals_shouldWorkForSameValues() {
        FileUploadProperties props1 = new FileUploadProperties(
            1024L, "pdf", 255, null, 50, null, null
        );
        FileUploadProperties props2 = new FileUploadProperties(
            1024L, "pdf", 255, null, 50, null, null
        );

        assertEquals(props1, props2);
    }

    @Test
    void toString_shouldReturnMeaningfulString() {
        FileUploadProperties props = new FileUploadProperties(
            1024L, "pdf", 255, null, 50, null, null
        );

        String str = props.toString();
        assertNotNull(str);
        assertTrue(str.contains("1024"));
    }
}