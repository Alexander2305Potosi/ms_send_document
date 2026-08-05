package com.example.fileprocessor.infrastructure.util;

import com.example.fileprocessor.domain.exception.ProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionMapperTest {

    @Test
    void testClassifyProcessingException() {
        ProcessingException ex = new ProcessingException("Custom error", "CUSTOM_CODE");
        ExceptionMapper.ErrorClassification classification = ExceptionMapper.classify(ex);
        assertEquals("CUSTOM_CODE", classification.code());
        assertEquals("Custom error", classification.message());
    }

    @Test
    void testClassifyWebClientResponseExceptions() {
        // HTTP 400
        WebClientResponseException ex400 = WebClientResponseException.create(400, "Bad Request", null, "body".getBytes(), null);
        assertEquals("DEST_BAD_REQUEST", ExceptionMapper.classify(ex400).code());

        // HTTP 401
        WebClientResponseException ex401 = WebClientResponseException.create(401, "Unauthorized", null, "body".getBytes(), null);
        assertEquals("DEST_UNAUTHORIZED", ExceptionMapper.classify(ex401).code());

        // HTTP 404
        WebClientResponseException ex404 = WebClientResponseException.create(404, "Not Found", null, "body".getBytes(), null);
        assertEquals("SOURCE_NOT_FOUND", ExceptionMapper.classify(ex404).code());

        // HTTP 429
        WebClientResponseException ex429 = WebClientResponseException.create(429, "Too Many Requests", null, "body".getBytes(), null);
        assertEquals("SOURCE_RATE_LIMIT", ExceptionMapper.classify(ex429).code());

        // HTTP 500
        WebClientResponseException ex500 = WebClientResponseException.create(500, "Internal Server Error", null, "body".getBytes(), null);
        assertEquals("BAD_GATEWAY", ExceptionMapper.classify(ex500).code());

        // HTTP 302
        WebClientResponseException ex302 = WebClientResponseException.create(302, "Found", null, "body".getBytes(), null);
        assertEquals("UNKNOWN_ERROR", ExceptionMapper.classify(ex302).code());
    }

    @Test
    void testClassifyTimeoutExceptions() {
        TimeoutException te = new TimeoutException("timeout");
        assertEquals("GATEWAY_TIMEOUT", ExceptionMapper.classify(te).code());

        RuntimeException wrappedTe = new RuntimeException(te);
        assertEquals("GATEWAY_TIMEOUT", ExceptionMapper.classify(wrappedTe).code());
    }

    @Test
    void testClassifyConnectionExceptions() {
        ConnectException ce = new ConnectException("connection failed");
        assertEquals("SERVICE_UNAVAILABLE", ExceptionMapper.classify(ce).code());

        RuntimeException wrappedCe = new RuntimeException(ce);
        assertEquals("SERVICE_UNAVAILABLE", ExceptionMapper.classify(wrappedCe).code());
    }

    @Test
    void testClassifyFallback() {
        RuntimeException ex = new RuntimeException("Generic error");
        ExceptionMapper.ErrorClassification classification = ExceptionMapper.classify(ex);
        assertEquals("UNKNOWN_ERROR", classification.code());
        assertEquals("Generic error", classification.message());
    }

    @Test
    void testClassifyWithNullOrBlankMessage() {
        RuntimeException ex = new RuntimeException((String) null);
        ExceptionMapper.ErrorClassification classification = ExceptionMapper.classify(ex);
        assertEquals("RuntimeException", classification.message());
    }

    @Test
    void testPrivateConstructorCanBeCalledViaReflection() throws Exception {
        java.lang.reflect.Constructor<ExceptionMapper> constructor = ExceptionMapper.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        ExceptionMapper instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
