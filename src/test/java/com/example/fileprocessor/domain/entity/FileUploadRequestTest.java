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
    void content_whenNull_returnsNull() {
        FileUploadRequest request = FileUploadRequest.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .build();

        assertNull(request.getContent());
    }
}
