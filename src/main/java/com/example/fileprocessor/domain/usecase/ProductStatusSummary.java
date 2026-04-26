package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductStatus;

/**
 * Immutable summary of product status with document counts.
 * Used for monitoring and reporting.
 */
public record ProductStatusSummary(
    String productId,
    int totalDocuments,
    int successCount,
    int failureCount,
    int pendingCount,
    int skippedCount,
    int notSentCount,
    int retryCount,
    ProductStatus overallStatus
) {
    public static ProductStatusSummaryBuilder builder() {
        return new ProductStatusSummaryBuilder();
    }

    public static class ProductStatusSummaryBuilder {
        private String productId;
        private int totalDocuments;
        private int successCount;
        private int failureCount;
        private int pendingCount;
        private int skippedCount;
        private int notSentCount;
        private int retryCount;
        private ProductStatus overallStatus;

        public ProductStatusSummaryBuilder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public ProductStatusSummaryBuilder totalDocuments(int totalDocuments) {
            this.totalDocuments = totalDocuments;
            return this;
        }

        public ProductStatusSummaryBuilder successCount(int successCount) {
            this.successCount = successCount;
            return this;
        }

        public ProductStatusSummaryBuilder failureCount(int failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public ProductStatusSummaryBuilder pendingCount(int pendingCount) {
            this.pendingCount = pendingCount;
            return this;
        }

        public ProductStatusSummaryBuilder skippedCount(int skippedCount) {
            this.skippedCount = skippedCount;
            return this;
        }

        public ProductStatusSummaryBuilder notSentCount(int notSentCount) {
            this.notSentCount = notSentCount;
            return this;
        }

        public ProductStatusSummaryBuilder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public ProductStatusSummaryBuilder overallStatus(ProductStatus overallStatus) {
            this.overallStatus = overallStatus;
            return this;
        }

        public ProductStatusSummary build() {
            return new ProductStatusSummary(
                productId, totalDocuments, successCount, failureCount,
                pendingCount, skippedCount, notSentCount, retryCount, overallStatus
            );
        }
    }
}
