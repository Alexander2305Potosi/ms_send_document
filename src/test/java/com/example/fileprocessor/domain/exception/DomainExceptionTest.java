package com.example.fileprocessor.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionTest {

    @Test
    void constructor_shouldSetMessageAndErrorCode() {
        // New order: (message, errorCode, cause)
        DomainException exception = new ProcessingException("File too large", "SIZE_ERROR", (Throwable) null);

        assertEquals("File too large", exception.getMessage());
        assertEquals("SIZE_ERROR", exception.getErrorCode());
    }

    @Test
    void getErrorCode_shouldReturnCorrectValue() {
        ProcessingException exception = new ProcessingException("Invalid extension", "EXT_ERROR", (Throwable) null);

        assertEquals("EXT_ERROR", exception.getErrorCode());
    }

    @Test
    void extendsRuntimeException_shouldBeUnchecked() {
        DomainException exception = new ProcessingException("test", "TEST", (Throwable) null);

        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void getMessage_shouldReturnPassedMessage() {
        String message = "Custom validation message";
        ProcessingException exception = new ProcessingException(message, "CODE", (Throwable) null);

        assertEquals(message, exception.getMessage());
    }
}