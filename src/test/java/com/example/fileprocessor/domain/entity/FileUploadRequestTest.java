package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadRequestTest {

    @Test
    void builder_createsValidRequest() {
        FileUploadRequest request = FileUploadRequest.builder()
            .documentId("doc-1")
            .content(new byte[]{1, 2, 3})
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(3L)
            .parentFolder("parent")
            .childFolder("child")
            .origin("origin")
            .build();

        assertEquals("doc-1", request.getDocumentId());
        assertArrayEquals(new byte[]{1, 2, 3}, request.getContent());
        assertEquals("test.pdf", request.getFilename());
        assertEquals("application/pdf", request.getContentType());
        assertEquals(3L, request.getFileSize());
        assertEquals("parent", request.getParentFolder());
        assertEquals("child", request.getChildFolder());
        assertEquals("origin", request.getOrigin());
    }

    @Test
    void content_whenNull_defaultsToEmptyArray() {
        FileUploadRequest request = FileUploadRequest.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .build();

        assertArrayEquals(new byte[0], request.getContent());
    }

    @Test
    void of_createsValidRequest() {
        FileUploadRequest request = FileUploadRequest.of(
            "doc-1",
            new byte[]{1, 2},
            "test.csv",
            "text/csv",
            2L,
            ".",
            "."
        );

        assertEquals("doc-1", request.getDocumentId());
        assertArrayEquals(new byte[]{1, 2}, request.getContent());
        assertEquals("test.csv", request.getFilename());
        assertEquals("text/csv", request.getContentType());
        assertEquals(".", request.getParentFolder());
        assertEquals(".", request.getChildFolder());
    }
}
