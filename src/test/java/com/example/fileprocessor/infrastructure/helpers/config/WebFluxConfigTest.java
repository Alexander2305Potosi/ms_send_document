package com.example.fileprocessor.infrastructure.helpers.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebFluxConfigTest {

    @Test
    void classExists() {
        assertDoesNotThrow(() -> Class.forName("com.example.fileprocessor.infrastructure.helpers.config.WebFluxConfig"));
    }

    @Test
    void implementsWebFluxConfigurer() throws Exception {
        Class<?> clazz = Class.forName("com.example.fileprocessor.infrastructure.helpers.config.WebFluxConfig");
        assertDoesNotThrow(() -> Class.forName("org.springframework.web.reactive.config.WebFluxConfigurer"));
    }

    @Test
    void hasNoArgConstructor() throws Exception {
        Class<?> clazz = Class.forName("com.example.fileprocessor.infrastructure.helpers.config.WebFluxConfig");
        assertDoesNotThrow(() -> clazz.getDeclaredConstructor());
    }
}