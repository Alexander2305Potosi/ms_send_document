package com.example.fileprocessor.domain.entity;

/**
 * Product processing status constants.
 * These represent the overall state of a Product based on its documents.
 */
public enum ProductStatus {
    /**
     * Product has pending documents to process.
     */
    PENDING,

    /**
     * Product is currently being processed.
     */
    PROCESSING,

    /**
     * All documents processed successfully.
     */
    SUCCESS,

    /**
     * At least one document failed permanently.
     */
    PARTIAL_FAILURE,

    /**
     * All documents processed - some skipped but none failed.
     */
    COMPLETED_WITH_SKIPS,

    /**
     * Product was processed but some documents were not sent (size, type, origin rules).
     */
    COMPLETED_WITH_NOT_SENT,

    /**
     * All documents failed.
     */
    COMPLETED_WITH_FAILURES;

}
