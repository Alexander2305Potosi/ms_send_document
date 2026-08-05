package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadRequestTest {

    @Test
    void builderCreatesValidRequest() {
        ProductUploadRequest request = ProductUploadRequest.builder()
            .documentId("doc-1")
            .content(new byte[]{1, 2, 3})
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(3L)
            .build();

        assertEquals("doc-1", request.getDocumentId());
        assertArrayEquals(new byte[]{1, 2, 3}, request.getContent());
        assertEquals("test.pdf", request.getFilename());
        assertEquals("application/pdf", request.getContentType());
        assertEquals(3L, request.getFileSize());
    }

    @Test
    void contentWhenNullReturnsNull() {
        ProductUploadRequest request = ProductUploadRequest.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .build();

        assertNull(request.getContent());
    }

    @Test
    void animalUploadRequestBuilderIncludesAnimalFields() {
        AnimalUploadRequest request = AnimalUploadRequest.builder()
            .documentId("doc-2")
            .filename("animal.pdf")
            .animalId("animal-1")
            .raza("Labrador")
            .tipo("Perro")
            .build();

        assertEquals("doc-2", request.getDocumentId());
        assertEquals("animal-1", request.getAnimalId());
        assertEquals("Labrador", request.getRaza());
        assertEquals("Perro", request.getTipo());
    }
}
