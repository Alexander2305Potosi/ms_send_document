package com.example.fileprocessor.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionTest {

    @Test
    void constructor_shouldSetMessageAndErrorCode() {
        DomainException exception = new FileValidationException("File too large", "SIZE_ERROR");

        assertEquals("File too large", exception.getMessage());
        assertEquals("SIZE_ERROR", exception.getErrorCode());
    }

    @Test
    void getErrorCode_shouldReturnCorrectValue() {
        FileValidationException exception = new FileValidationException("Invalid extension", "EXT_ERROR");

        assertEquals("EXT_ERROR", exception.getErrorCode());
    }

    @Test
    void extendsRuntimeException_shouldBeUnchecked() {
        DomainException exception = new FileValidationException("test", "TEST");

        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void getMessage_shouldReturnPassedMessage() {
        String message = "Custom validation message";
        FileValidationException exception = new FileValidationException(message, "CODE");

        assertEquals(message, exception.getMessage());
    }
}