package com.example.fileprocessor.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileValidationExceptionTest {

    @Test
    void constructor_shouldSetMessageAndErrorCode() {
        FileValidationException exception = new FileValidationException("Invalid file", "INVALID_FILE");

        assertEquals("Invalid file", exception.getMessage());
        assertEquals("INVALID_FILE", exception.getErrorCode());
    }

    @Test
    void extendsDomainException() {
        FileValidationException exception = new FileValidationException("test", "TEST");

        assertTrue(exception instanceof DomainException);
    }
}