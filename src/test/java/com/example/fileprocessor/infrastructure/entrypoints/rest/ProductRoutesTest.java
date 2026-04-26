package com.example.fileprocessor.infrastructure.entrypoints.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductRoutesTest {

    @Test
    void classExists() {
        assertDoesNotThrow(() -> Class.forName("com.example.fileprocessor.infrastructure.entrypoints.rest.ProductRoutes"));
    }

    @Test
    void routerMethodExists() throws Exception {
        Class<?> clazz = Class.forName("com.example.fileprocessor.infrastructure.entrypoints.rest.ProductRoutes");
        assertDoesNotThrow(() -> clazz.getMethod("productRouter",
            Class.forName("com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler")));
    }
}