package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRestPropertiesTest {

    @Test
    void record_shouldCreateInstance() {
        DocumentRestProperties props = new DocumentRestProperties(
            "http://localhost:8080",
            "/list",
            "/get",
            "/products",
            "/products/{productId}/documents",
            30
        );

        assertEquals("http://localhost:8080", props.endpoint());
        assertEquals("/list", props.listPath());
        assertEquals("/get", props.getPath());
        assertEquals("/products", props.productsPath());
        assertEquals("/products/{productId}/documents", props.productDocumentsPath());
        assertEquals(30, props.timeoutSeconds());
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        DocumentRestProperties props = new DocumentRestProperties(
            "http://api.example.com",
            "/v1/list",
            "/v1/get",
            "/v1/products",
            "/v1/products/{productId}/documents",
            60
        );

        assertEquals("http://api.example.com", props.endpoint());
        assertEquals("/v1/list", props.listPath());
        assertEquals("/v1/get", props.getPath());
        assertEquals("/v1/products", props.productsPath());
        assertEquals(60, props.timeoutSeconds());
    }

    @Test
    void equals_shouldWorkForSameValues() {
        DocumentRestProperties props1 = new DocumentRestProperties(
            "http://localhost", "/path", "/get", "/list", "/docs", 30
        );
        DocumentRestProperties props2 = new DocumentRestProperties(
            "http://localhost", "/path", "/get", "/list", "/docs", 30
        );

        assertEquals(props1, props2);
    }

    @Test
    void toString_shouldContainValues() {
        DocumentRestProperties props = new DocumentRestProperties(
            "http://localhost", "/path", "/get", "/list", "/docs", 30
        );

        String str = props.toString();
        assertNotNull(str);
        assertTrue(str.contains("localhost"));
    }
}