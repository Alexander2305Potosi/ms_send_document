package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductStatus;
import lombok.Builder;

/**
 * Immutable summary of product status with document counts.
 * Used for monitoring and reporting.
 */
@Builder
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
) {}