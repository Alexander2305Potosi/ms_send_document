package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregates document statuses to determine overall product status.
 *
 * Business Rules:
 * - CA-01: SUCCESS when ALL documents are SUCCESS
 * - CA-02: PARTIAL_FAILURE if at least 1 document FAILED permanently
 * - CA-03: COMPLETED_WITH_SKIPS if all processed (no FAILURE/RETRY) but some are SKIPPED
 *
 * Additional rules:
 * - COMPLETED_WITH_NOT_SENT: If all documents are either SUCCESS, SKIPPED, or NOT_SENT
 * - COMPLETED_WITH_FAILURES: If all documents are FAILURE
 * - PENDING: If any document is still PENDING, PROCESSING, or RETRY
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
     * Call this after each document status change.
     */
    public Mono<Void> updateProductStatus(String productId, String traceId) {
        return documentRepository.findByProductId(productId)
            .collectList()
            .flatMap(docs -> {
                ProductStatus newStatus = calculateStatus(docs);
                log.info("Calculated status for product {}: {} (docs: success={}, failure={}, pending={}, skipped={}, notSent={})",
                    productId, newStatus,
                    countByStatus(docs, DocumentStatus.SUCCESS_VALUE),
                    countByStatus(docs, DocumentStatus.FAILURE_VALUE),
                    countByStatus(docs, DocumentStatus.PENDING_VALUE),
                    countByStatus(docs, DocumentStatus.SKIPPED_VALUE),
                    countByStatus(docs, DocumentStatus.NOT_SENT_VALUE));

                return productRepository.updateStatus(productId, newStatus.name(), traceId);
            });
    }

    /**
     * Calculates the overall status from a list of documents.
     */
    public static ProductStatus calculateStatus(java.util.List<ProductDocumentToProcess> documents) {
        if (documents == null || documents.isEmpty()) {
            return ProductStatus.PENDING;
        }

        int success = countByStatus(documents, DocumentStatus.SUCCESS_VALUE);
        int failure = countByStatus(documents, DocumentStatus.FAILURE_VALUE);
        int pending = countByStatus(documents, DocumentStatus.PENDING_VALUE);
        int processing = countByStatus(documents, DocumentStatus.PROCESSING_VALUE);
        int retry = countByStatus(documents, DocumentStatus.RETRY_VALUE);
        int skipped = countByStatus(documents, DocumentStatus.SKIPPED_VALUE);
        int notSent = countByStatus(documents, DocumentStatus.NOT_SENT_VALUE);
        int total = documents.size();

        // If any document is still being processed or pending, product is not complete
        if (pending > 0 || processing > 0 || retry > 0) {
            return ProductStatus.PENDING;
        }

        // CA-02: If any document failed permanently, it's PARTIAL_FAILURE
        if (failure > 0) {
            return ProductStatus.PARTIAL_FAILURE;
        }

        // All documents processed (no PENDING, PROCESSING, RETRY, FAILURE)
        int processed = success + skipped + notSent;

        if (processed == total) {
            // CA-01: All SUCCESS
            if (success == total) {
                return ProductStatus.SUCCESS;
            }
            // CA-03: All processed but some are SKIPPED (no failures)
            if (skipped > 0 && success + skipped == total) {
                return ProductStatus.COMPLETED_WITH_SKIPS;
            }
            // Some were NOT_SENT due to business rules
            return ProductStatus.COMPLETED_WITH_NOT_SENT;
        }

        // All documents are FAILURE
        if (failure == total) {
            return ProductStatus.COMPLETED_WITH_FAILURES;
        }

        return ProductStatus.PENDING;
    }

    private static int countByStatus(java.util.List<ProductDocumentToProcess> documents, String status) {
        return (int) documents.stream()
            .filter(doc -> status.equals(doc.getStatus()))
            .count();
    }

    /**
     * Returns a summary of document counts by status for a product.
     */
    public static ProductStatusSummary createSummary(String productId,
                                                     java.util.List<ProductDocumentToProcess> documents) {
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

        int success = countByStatus(documents, DocumentStatus.SUCCESS_VALUE);
        int failure = countByStatus(documents, DocumentStatus.FAILURE_VALUE);
        int pending = countByStatus(documents, DocumentStatus.PENDING_VALUE);
        int processing = countByStatus(documents, DocumentStatus.PROCESSING_VALUE);
        int retry = countByStatus(documents, DocumentStatus.RETRY_VALUE);
        int skipped = countByStatus(documents, DocumentStatus.SKIPPED_VALUE);
        int notSent = countByStatus(documents, DocumentStatus.NOT_SENT_VALUE);

        return ProductStatusSummary.builder()
            .productId(productId)
            .totalDocuments(documents.size())
            .successCount(success)
            .failureCount(failure)
            .pendingCount(pending + processing + retry)  // Active processing
            .skippedCount(skipped)
            .notSentCount(notSent)
            .retryCount(retry)
            .overallStatus(calculateStatus(documents))
            .build();
    }
}
