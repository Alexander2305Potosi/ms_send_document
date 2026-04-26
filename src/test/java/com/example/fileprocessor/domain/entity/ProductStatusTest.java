package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductStatusTest {

    @Test
    void enumValues_shouldBeCorrect() {
        assertEquals(7, ProductStatus.values().length);
        assertNotNull(ProductStatus.PENDING);
        assertNotNull(ProductStatus.PROCESSING);
        assertNotNull(ProductStatus.SUCCESS);
        assertNotNull(ProductStatus.PARTIAL_FAILURE);
        assertNotNull(ProductStatus.COMPLETED_WITH_SKIPS);
        assertNotNull(ProductStatus.COMPLETED_WITH_NOT_SENT);
        assertNotNull(ProductStatus.COMPLETED_WITH_FAILURES);
    }

    @Test
    void valueConstants_shouldMatchEnumNames() {
        assertEquals("PENDING", ProductStatus.PENDING_VALUE);
        assertEquals("PROCESSING", ProductStatus.PROCESSING_VALUE);
        assertEquals("SUCCESS", ProductStatus.SUCCESS_VALUE);
        assertEquals("PARTIAL_FAILURE", ProductStatus.PARTIAL_FAILURE_VALUE);
        assertEquals("COMPLETED_WITH_SKIPS", ProductStatus.COMPLETED_WITH_SKIPS_VALUE);
        assertEquals("COMPLETED_WITH_NOT_SENT", ProductStatus.COMPLETED_WITH_NOT_SENT_VALUE);
        assertEquals("COMPLETED_WITH_FAILURES", ProductStatus.COMPLETED_WITH_FAILURES_VALUE);
    }
}