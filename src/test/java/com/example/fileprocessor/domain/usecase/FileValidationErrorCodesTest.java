package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileValidationErrorCodesTest {

    @Test
    void errorCodes_shouldNotBeEmpty() {
        assertNotNull(FileValidationErrorCodes.FILE_SIZE_EXCEEDED);
        assertNotNull(FileValidationErrorCodes.INVALID_FILE_TYPE);
        assertNotNull(FileValidationErrorCodes.FILENAME_TOO_LONG);
        assertNotNull(FileValidationErrorCodes.INVALID_FILENAME);
    }

    @Test
    void errorCodes_shouldBeUppercase() {
        assertEquals("FILE_SIZE_EXCEEDED", FileValidationErrorCodes.FILE_SIZE_EXCEEDED);
        assertEquals("INVALID_FILE_TYPE", FileValidationErrorCodes.INVALID_FILE_TYPE);
        assertEquals("FILENAME_TOO_LONG", FileValidationErrorCodes.FILENAME_TOO_LONG);
        assertEquals("INVALID_FILENAME", FileValidationErrorCodes.INVALID_FILENAME);
    }

    @Test
    void messageConstants_shouldNotBeEmpty() {
        assertNotNull(FileValidationErrorCodes.MSG_FILE_SIZE_EXCEEDED);
        assertNotNull(FileValidationErrorCodes.MSG_FILE_TYPE_NOT_ALLOWED);
        assertNotNull(FileValidationErrorCodes.MSG_FILENAME_TOO_LONG);
        assertNotNull(FileValidationErrorCodes.MSG_FILENAME_INVALID);
    }

    @Test
    void messageConstants_shouldContainDescriptiveText() {
        assertTrue(FileValidationErrorCodes.MSG_FILE_SIZE_EXCEEDED.contains("size"));
        assertTrue(FileValidationErrorCodes.MSG_FILE_TYPE_NOT_ALLOWED.contains("type"));
        assertTrue(FileValidationErrorCodes.MSG_FILENAME_TOO_LONG.contains("length"));
    }
}