package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductInfoTest {

    @Test
    void builder_shouldCreateInstance() {
        ProductInfo info = ProductInfo.builder()
            .productId("prod-1")
            .name("Test Product")
            .documents(List.of())
            .build();

        assertEquals("prod-1", info.getProductId());
        assertEquals("Test Product", info.getName());
        assertNotNull(info.getDocuments());
    }

    @Test
    void builder_withDocuments_shouldCreateInstance() {
        ProductDocumentInfo doc1 = ProductDocumentInfo.builder()
            .documentId("doc-1")
            .filename("file1.pdf")
            .content(new byte[]{1})
            .contentType("application/pdf")
            .size(1)
            .isZip(false)
            .origin("incoming")
            .build();

        ProductInfo info = ProductInfo.builder()
            .productId("prod-2")
            .name("Product with Docs")
            .documents(List.of(doc1))
            .build();

        assertEquals("prod-2", info.getProductId());
        assertEquals(1, info.getDocuments().size());
        assertEquals("doc-1", info.getDocuments().get(0).documentId());
    }
}