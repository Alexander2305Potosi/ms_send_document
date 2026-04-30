package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStatusTest {

    @Test
    void allStatuses_areDefined() {
        assertNotNull(DocumentStatus.SUCCESS);
        assertNotNull(DocumentStatus.FAILURE);
        assertNotNull(DocumentStatus.PENDING);
        assertNotNull(DocumentStatus.PROCESSING);
        assertNotNull(DocumentStatus.RETRY);
        assertNotNull(DocumentStatus.SKIPPED);
    }

    @Test
    void success_hasCorrectValue() {
        assertEquals("SUCCESS", DocumentStatus.SUCCESS.name());
    }

    @Test
    void failure_hasCorrectValue() {
        assertEquals("FAILURE", DocumentStatus.FAILURE.name());
    }

    @Test
    void pending_hasCorrectValue() {
        assertEquals("PENDING", DocumentStatus.PENDING.name());
    }

    @Test
    void processing_hasCorrectValue() {
        assertEquals("PROCESSING", DocumentStatus.PROCESSING.name());
    }

    @Test
    void retry_hasCorrectValue() {
        assertEquals("RETRY", DocumentStatus.RETRY.name());
    }

    @Test
    void skipped_hasCorrectValue() {
        assertEquals("SKIPPED", DocumentStatus.SKIPPED.name());
    }
}
