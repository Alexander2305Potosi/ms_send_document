package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessingConstantsTest {

    @Test
    void constants_shouldHaveCorrectValues() {
        assertEquals(0, DocumentProcessingConstants.DEFAULT_RETRY_COUNT);
        assertEquals(10, DocumentProcessingConstants.DEFAULT_MAX_CONCURRENCY);
    }

    @Test
    void messageConstants_shouldNotBeEmpty() {
        assertNotNull(DocumentProcessingConstants.MSG_SKIPPED_FOLDER);
        assertNotNull(DocumentProcessingConstants.MSG_NOT_SENT_ORIGIN);
        assertNotNull(DocumentProcessingConstants.MSG_SIZE_EXCEEDED);
        assertNotNull(DocumentProcessingConstants.MSG_SIZE_EXCEEDED_SUFFIX);
        assertNotNull(DocumentProcessingConstants.MSG_CIRCUIT_BREAKER_OPEN);
    }

    @Test
    void messageConstants_shouldContainExpectedText() {
        assertTrue(DocumentProcessingConstants.MSG_SKIPPED_FOLDER.contains("folder"));
        assertTrue(DocumentProcessingConstants.MSG_NOT_SENT_ORIGIN.contains("origin"));
        assertTrue(DocumentProcessingConstants.MSG_SIZE_EXCEEDED.contains("size"));
        assertTrue(DocumentProcessingConstants.MSG_CIRCUIT_BREAKER_OPEN.contains("Circuit breaker"));
    }
}