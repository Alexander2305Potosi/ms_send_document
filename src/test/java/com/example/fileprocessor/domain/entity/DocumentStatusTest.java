package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStatusTest {

    @Test
    void enumValues_shouldBeCorrect() {
        assertEquals(7, DocumentStatus.values().length);
        assertNotNull(DocumentStatus.PENDING);
        assertNotNull(DocumentStatus.PROCESSING);
        assertNotNull(DocumentStatus.SUCCESS);
        assertNotNull(DocumentStatus.FAILURE);
        assertNotNull(DocumentStatus.RETRY);
        assertNotNull(DocumentStatus.SKIPPED);
        assertNotNull(DocumentStatus.NOT_SENT);
    }

    @Test
    void valueConstants_shouldMatchEnumNames() {
        assertEquals("PENDING", DocumentStatus.PENDING_VALUE);
        assertEquals("PROCESSING", DocumentStatus.PROCESSING_VALUE);
        assertEquals("SUCCESS", DocumentStatus.SUCCESS_VALUE);
        assertEquals("FAILURE", DocumentStatus.FAILURE_VALUE);
        assertEquals("RETRY", DocumentStatus.RETRY_VALUE);
        assertEquals("SKIPPED", DocumentStatus.SKIPPED_VALUE);
        assertEquals("NOT_SENT", DocumentStatus.NOT_SENT_VALUE);
    }
}