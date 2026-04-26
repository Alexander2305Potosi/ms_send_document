package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ProductStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductStatusAggregatorTest {

    @Test
    void calculateStatus_withAllSuccess_shouldReturnSuccess() {
        List<ProductDocumentToProcess> docs = List.of(
            createDoc("d1", DocumentStatus.SUCCESS_VALUE),
            createDoc("d2", DocumentStatus.SUCCESS_VALUE),
            createDoc("d3", DocumentStatus.SUCCESS_VALUE)
        );

        ProductStatus result = ProductStatusAggregator.calculateStatus(docs);

        assertEquals(ProductStatus.SUCCESS, result);
    }

    @Test
    void calculateStatus_withPendingDocument_shouldReturnPending() {
        List<ProductDocumentToProcess> docs = List.of(
            createDoc("d1", DocumentStatus.SUCCESS_VALUE),
            createDoc("d2", DocumentStatus.PENDING_VALUE)
        );

        ProductStatus result = ProductStatusAggregator.calculateStatus(docs);

        assertEquals(ProductStatus.PENDING, result);
    }

    @Test
    void calculateStatus_withPartialFailure_shouldReturnPartialFailure() {
        List<ProductDocumentToProcess> docs = List.of(
            createDoc("d1", DocumentStatus.SUCCESS_VALUE),
            createDoc("d2", DocumentStatus.FAILURE_VALUE),
            createDoc("d3", DocumentStatus.SUCCESS_VALUE)
        );

        ProductStatus result = ProductStatusAggregator.calculateStatus(docs);

        assertEquals(ProductStatus.PARTIAL_FAILURE, result);
    }

    @Test
    void calculateStatus_withEmptyList_shouldReturnPending() {
        List<ProductDocumentToProcess> docs = List.of();

        ProductStatus result = ProductStatusAggregator.calculateStatus(docs);

        assertEquals(ProductStatus.PENDING, result);
    }

    @Test
    void calculateStatus_withNullList_shouldReturnPending() {
        ProductStatus result = ProductStatusAggregator.calculateStatus(null);

        assertEquals(ProductStatus.PENDING, result);
    }

    @Test
    void calculateStatus_withProcessingStatus_shouldReturnPending() {
        List<ProductDocumentToProcess> docs = List.of(
            createDoc("d1", DocumentStatus.SUCCESS_VALUE),
            createDoc("d2", DocumentStatus.PROCESSING_VALUE)
        );

        ProductStatus result = ProductStatusAggregator.calculateStatus(docs);

        assertEquals(ProductStatus.PENDING, result);
    }

    @Test
    void createSummary_shouldCalculateCorrectCounts() {
        List<ProductDocumentToProcess> docs = List.of(
            createDoc("d1", DocumentStatus.SUCCESS_VALUE),
            createDoc("d2", DocumentStatus.SUCCESS_VALUE),
            createDoc("d3", DocumentStatus.FAILURE_VALUE),
            createDoc("d4", DocumentStatus.PENDING_VALUE)
        );

        ProductStatusSummary summary = ProductStatusAggregator.createSummary("prod-1", docs);

        assertEquals("prod-1", summary.productId());
        assertEquals(4, summary.totalDocuments());
        assertEquals(2, summary.successCount());
        assertEquals(1, summary.failureCount());
    }

    private ProductDocumentToProcess createDoc(String id, String status) {
        return ProductDocumentToProcess.builder()
            .documentId(id)
            .productId("prod-1")
            .filename(id + ".pdf")
            .content(new byte[]{1})
            .contentType("application/pdf")
            .status(status)
            .build();
    }
}