package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRestPropertiesTest {

    @Test
    void recordCreatesValidProperties() {
        DocumentRestProperties props = new DocumentRestProperties(
            "http://localhost:3001",
            "/api/products",
            "/api/products/{productId}/documents",
            15
        );

        assertEquals("http://localhost:3001", props.endpoint());
        assertEquals("/api/products", props.productsPath());
        assertEquals("/api/products/{productId}/documents", props.productDocumentsPath());
        assertEquals(15, props.timeoutSeconds());
    }

    @Test
    void productDocumentsPathWithReplacement() {
        DocumentRestProperties props = new DocumentRestProperties(
            "http://localhost:3001",
            "/api/products",
            "/api/products/{productId}/documents",
            15
        );

        String path = props.productDocumentsPath().replace("{productId}", "prod-123");
        assertEquals("/api/products/prod-123/documents", path);
    }
}
