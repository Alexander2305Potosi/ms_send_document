package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductStatusSummaryTest {

    @Test
    void record_shouldCreateInstance() {
        ProductStatusSummary summary = new ProductStatusSummary(
            "prod-1",
            10,
            5,
            2,
            1,
            1,
            1,
            0,
            ProductStatus.SUCCESS
        );

        assertEquals("prod-1", summary.productId());
        assertEquals(10, summary.totalDocuments());
        assertEquals(5, summary.successCount());
        assertEquals(2, summary.failureCount());
        assertEquals(1, summary.pendingCount());
        assertEquals(1, summary.skippedCount());
        assertEquals(1, summary.notSentCount());
        assertEquals(0, summary.retryCount());
        assertEquals(ProductStatus.SUCCESS, summary.overallStatus());
    }

    @Test
    void builder_shouldCreateInstance() {
        ProductStatusSummary summary = ProductStatusSummary.builder()
            .productId("prod-2")
            .totalDocuments(20)
            .successCount(15)
            .failureCount(3)
            .pendingCount(2)
            .skippedCount(0)
            .notSentCount(0)
            .retryCount(0)
            .overallStatus(ProductStatus.PARTIAL_FAILURE)
            .build();

        assertEquals("prod-2", summary.productId());
        assertEquals(20, summary.totalDocuments());
        assertEquals(15, summary.successCount());
        assertEquals(ProductStatus.PARTIAL_FAILURE, summary.overallStatus());
    }

    @Test
    void builder_shouldAllowPartialBuild() {
        ProductStatusSummary summary = ProductStatusSummary.builder()
            .productId("prod-3")
            .totalDocuments(5)
            .overallStatus(ProductStatus.PENDING)
            .build();

        assertEquals("prod-3", summary.productId());
        assertEquals(5, summary.totalDocuments());
        assertEquals(0, summary.successCount());
        assertEquals(ProductStatus.PENDING, summary.overallStatus());
    }

    @Test
    void equals_sameValues_shouldBeEqual() {
        ProductStatusSummary s1 = new ProductStatusSummary("p", 1, 1, 0, 0, 0, 0, 0, ProductStatus.SUCCESS);
        ProductStatusSummary s2 = new ProductStatusSummary("p", 1, 1, 0, 0, 0, 0, 0, ProductStatus.SUCCESS);

        assertEquals(s1, s2);
    }

    @Test
    void toString_shouldContainValues() {
        ProductStatusSummary summary = ProductStatusSummary.builder()
            .productId("test-prod")
            .totalDocuments(10)
            .build();

        String str = summary.toString();
        assertNotNull(str);
        assertTrue(str.contains("test-prod"));
    }
}