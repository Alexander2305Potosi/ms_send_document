package com.example.fileprocessor.domain.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionUtilsTest {

    @Test
    @DisplayName("Debe convertir un Throwable en String correctamente")
    void getStackTraceAsString_withException_returnsString() {
        Exception ex = new RuntimeException("Test error");
        String stackTrace = ExceptionUtils.getStackTraceAsString(ex);
        
        assertNotNull(stackTrace);
        assertTrue(stackTrace.contains("java.lang.RuntimeException: Test error"));
        assertTrue(stackTrace.contains("at com.example.fileprocessor.domain.util.ExceptionUtilsTest"));
    }

    @Test
    @DisplayName("Debe manejar Throwable nulo devolviendo vacío o mensaje informativo")
    void getStackTraceAsString_withNull_returnsEmpty() {
        String stackTrace = ExceptionUtils.getStackTraceAsString(null);
        assertEquals("", stackTrace);
    }

    @Test
    @DisplayName("Debe convertir una excepción con causa")
    void getStackTraceAsString_withCause_returnsFullTrace() {
        Exception cause = new IllegalArgumentException("Cause error");
        Exception ex = new RuntimeException("Main error", cause);
        
        String stackTrace = ExceptionUtils.getStackTraceAsString(ex);
        
        assertTrue(stackTrace.contains("Main error"));
        assertTrue(stackTrace.contains("Caused by: java.lang.IllegalArgumentException: Cause error"));
    }
}
