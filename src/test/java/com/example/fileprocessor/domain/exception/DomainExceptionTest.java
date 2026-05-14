package com.example.fileprocessor.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionTest {

    @Test
    void constructor_shouldSetMessageAndErrorCode() {
        DomainException exception = new ProcessingException("SIZE_ERROR", "File too large", (Throwable) null);

        assertEquals("File too large", exception.getMessage());
        assertEquals("SIZE_ERROR", exception.getErrorCode());
    }

    @Test
    void getErrorCode_shouldReturnCorrectValue() {
        ProcessingException exception = new ProcessingException("EXT_ERROR", "Invalid extension", (Throwable) null);

        assertEquals("EXT_ERROR", exception.getErrorCode());
    }

    @Test
    void extendsRuntimeException_shouldBeUnchecked() {
        DomainException exception = new ProcessingException("TEST", "test", (Throwable) null);

        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void getMessage_shouldReturnPassedMessage() {
        String message = "Custom validation message";
        ProcessingException exception = new ProcessingException("CODE", message, (Throwable) null);

        assertEquals(message, exception.getMessage());
    }
}