package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapRequestTest {

    @Test
    void builder_shouldCreateInstance() {
        SoapRequest request = SoapRequest.builder()
            .documentId("doc-1")
            .fileContent(new byte[]{1, 2, 3})
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(3)
            .traceId("trace-1")
            .parentFolder("incoming")
            .childFolder("docs")
            .build();

        assertEquals("doc-1", request.getDocumentId());
        assertArrayEquals(new byte[]{1, 2, 3}, request.getFileContent());
        assertEquals("test.pdf", request.getFilename());
        assertEquals("application/pdf", request.getContentType());
        assertEquals(3, request.getFileSize());
        assertEquals("trace-1", request.getTraceId());
        assertEquals("incoming", request.getParentFolder());
        assertEquals("docs", request.getChildFolder());
    }

    @Test
    void fromFileData_shouldCreateRequest() {
        FileData fileData = FileData.builder()
            .documentId("doc-2")
            .content(new byte[]{4, 5, 6})
            .filename("data.txt")
            .contentType("text/plain")
            .size(3)
            .traceId("trace-2")
            .build();

        SoapRequest request = SoapRequest.fromFileData(fileData, "archive", "processed");

        assertNull(request.getDocumentId()); // documentId is not set by fromFileData
        assertArrayEquals(new byte[]{4, 5, 6}, request.getFileContent());
        assertEquals("data.txt", request.getFilename());
        assertEquals("text/plain", request.getContentType());
        assertEquals(3, request.getFileSize());
        assertEquals("trace-2", request.getTraceId());
        assertEquals("archive", request.getParentFolder());
        assertEquals("processed", request.getChildFolder());
    }
}