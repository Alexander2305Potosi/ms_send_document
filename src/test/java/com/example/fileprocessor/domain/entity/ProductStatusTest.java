package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductStatusTest {

    @Test
    void allStatuses_areDefined() {
        assertNotNull(ProductStatus.PENDING);
        assertNotNull(ProductStatus.PROCESSING);
        assertNotNull(ProductStatus.SUCCESS);
        assertNotNull(ProductStatus.PARTIAL_FAILURE);
        assertNotNull(ProductStatus.COMPLETED_WITH_SKIPS);
        assertNotNull(ProductStatus.COMPLETED_WITH_NOT_SENT);
        assertNotNull(ProductStatus.COMPLETED_WITH_FAILURES);
    }

    @Test
    void pending_hasCorrectValue() {
        assertEquals("PENDING", ProductStatus.PENDING.name());
    }

    @Test
    void success_hasCorrectValue() {
        assertEquals("SUCCESS", ProductStatus.SUCCESS.name());
    }

    @Test
    void partialFailure_hasCorrectValue() {
        assertEquals("PARTIAL_FAILURE", ProductStatus.PARTIAL_FAILURE.name());
    }

    @Test
    void completedWithSkips_hasCorrectValue() {
        assertEquals("COMPLETED_WITH_SKIPS", ProductStatus.COMPLETED_WITH_SKIPS.name());
    }

    @Test
    void completedWithNotSent_hasCorrectValue() {
        assertEquals("COMPLETED_WITH_NOT_SENT", ProductStatus.COMPLETED_WITH_NOT_SENT.name());
    }

    @Test
    void completedWithFailures_hasCorrectValue() {
        assertEquals("COMPLETED_WITH_FAILURES", ProductStatus.COMPLETED_WITH_FAILURES.name());
    }
}
