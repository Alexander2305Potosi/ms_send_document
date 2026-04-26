package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentErrorCodesTest {

    @Test
    void errorCodes_shouldNotBeEmpty() {
        assertNotNull(DocumentErrorCodes.UNKNOWN_ERROR);
        assertNotNull(DocumentErrorCodes.TIMEOUT);
        assertNotNull(DocumentErrorCodes.GATEWAY_TIMEOUT);
        assertNotNull(DocumentErrorCodes.BAD_GATEWAY);
        assertNotNull(DocumentErrorCodes.CLIENT_ERROR);
        assertNotNull(DocumentErrorCodes.VALIDATION_ERROR);
        assertNotNull(DocumentErrorCodes.INVALID_RESPONSE);
        assertNotNull(DocumentErrorCodes.SKIPPED_FOLDER);
        assertNotNull(DocumentErrorCodes.NOT_SENT_ORIGIN);
        assertNotNull(DocumentErrorCodes.SIZE_EXCEEDED);
        assertNotNull(DocumentErrorCodes.CIRCUIT_BREAKER_OPEN);
    }

    @Test
    void timeoutRelated_shouldContainTimeout() {
        assertTrue(DocumentErrorCodes.TIMEOUT.contains("TIMEOUT") || DocumentErrorCodes.TIMEOUT.equals("TIMEOUT"));
        assertEquals("TIMEOUT", DocumentErrorCodes.TIMEOUT);
        assertEquals("GATEWAY_TIMEOUT", DocumentErrorCodes.GATEWAY_TIMEOUT);
    }

    @Test
    void messageConstants_shouldNotBeEmpty() {
        assertNotNull(DocumentErrorCodes.MSG_TIMEOUT);
        assertNotNull(DocumentErrorCodes.MSG_TIMEOUT_TITLE);
        assertNotNull(DocumentErrorCodes.MSG_VALIDATION);
    }
}