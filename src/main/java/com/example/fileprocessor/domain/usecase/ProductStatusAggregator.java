package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Aggregates document statuses to determine overall product status.
 */
public class ProductStatusAggregator {

    private static final Logger log = LoggerFactory.getLogger(ProductStatusAggregator.class);

    private final ProductDocumentRepository documentRepository;
    private final ProductRepository productRepository;

    public ProductStatusAggregator(ProductDocumentRepository documentRepository,
                                  ProductRepository productRepository) {
        this.documentRepository = documentRepository;
        this.productRepository = productRepository;
    }

    /**
     * Calculates and updates the status of a product based on its documents.
     */
    public Mono<Void> updateProductStatus(String productId) {
        return documentRepository.findByProductId(productId)
            .collectList()
            .flatMap(docs -> {
                var counts = countAllStatuses(docs);
                ProductStatus newStatus = calculateStatusFromCounts(counts);

                log.info("Calculated status for product {}: {} (success={}, failure={}, pending={})",
                    productId, newStatus,
                    counts.success, counts.failure, counts.pending);

                return productRepository.updateStatus(productId, newStatus.name());
            });
    }

    /**
     * Calculates the overall status from a list of documents.
     */
    public static ProductStatus calculateStatus(List<ProductDocumentToProcess> documents) {
        if (documents == null || documents.isEmpty()) {
            return ProductStatus.PENDING;
        }
        return calculateStatusFromCounts(countAllStatuses(documents));
    }

    /**
     * Returns a summary of document counts by status for a product.
     */
    public static ProductStatusSummary createSummary(String productId, List<ProductDocumentToProcess> documents) {
        if (documents == null || documents.isEmpty()) {
            return ProductStatusSummary.builder()
                .productId(productId)
                .totalDocuments(0)
                .successCount(0)
                .failureCount(0)
                .pendingCount(0)
                .skippedCount(0)
                .notSentCount(0)
                .retryCount(0)
                .overallStatus(ProductStatus.PENDING)
                .build();
        }

        var counts = countAllStatuses(documents);
        return ProductStatusSummary.builder()
            .productId(productId)
            .totalDocuments(documents.size())
            .successCount(counts.success)
            .failureCount(counts.failure)
            .pendingCount(counts.pending + counts.processing + counts.retry)
            .skippedCount(counts.skipped)
            .notSentCount(counts.notSent)
            .retryCount(counts.retry)
            .overallStatus(calculateStatusFromCounts(counts))
            .build();
    }

    // ============ PRIVATE HELPERS ============

    private record StatusCounts(
        int success, int failure, int pending,
        int processing, int retry, int skipped, int notSent
    ) {}

    private static StatusCounts countAllStatuses(List<ProductDocumentToProcess> documents) {
        int success = 0, failure = 0, pending = 0;
        int processing = 0, retry = 0, skipped = 0, notSent = 0;

        for (var doc : documents) {
            switch (doc.getStatus()) {
                case "SUCCESS" -> success++;
                case "FAILURE" -> failure++;
                case "PENDING" -> pending++;
                case "PROCESSING" -> processing++;
                case "RETRY" -> retry++;
                case "SKIPPED" -> skipped++;
                case "NOT_SENT" -> notSent++;
                default -> log.warn("Unknown document status '{}' for document {} in product {}",
                    doc.getStatus(), doc.getDocumentId(), doc.getProductId());
            }
        }
        return new StatusCounts(success, failure, pending, processing, retry, skipped, notSent);
    }

    private static ProductStatus calculateStatusFromCounts(StatusCounts c) {
        int total = c.success + c.failure + c.pending + c.processing + c.retry + c.skipped + c.notSent;

        if (c.pending > 0 || c.processing > 0 || c.retry > 0) {
            return ProductStatus.PENDING;
        }

        if (c.failure > 0) {
            return ProductStatus.PARTIAL_FAILURE;
        }

        if (c.success == total) {
            return ProductStatus.SUCCESS;
        }
        if (c.skipped > 0 && c.success + c.skipped == total) {
            return ProductStatus.COMPLETED_WITH_SKIPS;
        }
        if (c.notSent > 0 && c.success + c.notSent == total) {
            return ProductStatus.COMPLETED_WITH_NOT_SENT;
        }
        if (c.failure == total) {
            return ProductStatus.COMPLETED_WITH_FAILURES;
        }

        return ProductStatus.PENDING;
    }
}
