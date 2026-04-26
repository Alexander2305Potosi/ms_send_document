package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for document persistence operations.
 * Handles storage and retrieval of product documents.
 */
public interface ProductDocumentRepository {
    /**
     * Finds all documents with pending/processing/retry status.
     * @return Flux of documents awaiting processing
     */
    Flux<ProductDocumentToProcess> findPendingDocuments();

    /**
     * Finds pending documents for a specific product.
     * @param productId the product identifier
     * @return Flux of documents for the product
     */
    Flux<ProductDocumentToProcess> findPendingDocumentsByProduct(String productId);

    /**
     * Finds all documents for a specific product.
     * @param productId the product identifier
     * @return Flux of all documents for the product
     */
    Flux<ProductDocumentToProcess> findByProductId(String productId);

    /**
     * Claims a document for processing (sets status to PROCESSING).
     * Returns true if claim succeeded, false if already claimed or not pending.
     * @param documentId the document identifier
     * @return Mono<Boolean> true if claimed successfully
     */
    Mono<Boolean> claimDocument(String documentId);

    /**
     * Saves a single document.
     * @param document the document to save
     * @return Mono that completes when saved
     */
    Mono<Void> save(ProductDocumentToProcess document);

    /**
     * Saves multiple documents.
     * @param documents flux of documents to save
     * @return Mono that completes when all saved
     */
    Mono<Void> saveAll(Flux<ProductDocumentToProcess> documents);

    /**
     * Updates document status and metadata.
     * @param documentId the document identifier
     * @param status the new status
     * @param traceId the trace identifier
     * @param soapCorrelationId the SOAP correlation ID (if successful)
     * @param errorCode the error code (if failed)
     * @return Mono that completes when updated
     */
    Mono<Void> updateStatus(String documentId, String status, String traceId,
                            String soapCorrelationId, String errorCode);
}
