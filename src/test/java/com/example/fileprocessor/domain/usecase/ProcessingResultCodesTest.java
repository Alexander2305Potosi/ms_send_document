package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingResultCodesTest {

    @Test
    void allCodes_areNonNull() {
        assertNotNull(ProcessingResultCodes.EMPTY_CONTENT);
        assertNotNull(ProcessingResultCodes.INVALID_BASE64);
        assertNotNull(ProcessingResultCodes.INVALID_RESPONSE);
        assertNotNull(ProcessingResultCodes.UNKNOWN_ERROR);
    }

    @Test
    void codes_areNonEmpty() {
        assertFalse(ProcessingResultCodes.EMPTY_CONTENT.isBlank());
        assertFalse(ProcessingResultCodes.INVALID_BASE64.isBlank());
        assertFalse(ProcessingResultCodes.INVALID_RESPONSE.isBlank());
        assertFalse(ProcessingResultCodes.UNKNOWN_ERROR.isBlank());
    }

    @Test
    void emptyContent_isCorrectValue() {
        assertEquals("EMPTY_CONTENT", ProcessingResultCodes.EMPTY_CONTENT);
    }

    @Test
    void invalidBase64_isCorrectValue() {
        assertEquals("INVALID_BASE64", ProcessingResultCodes.INVALID_BASE64);
    }
}
