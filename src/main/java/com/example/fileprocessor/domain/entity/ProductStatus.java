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

    public static final String PENDING_VALUE = PENDING.name();
    public static final String PROCESSING_VALUE = PROCESSING.name();
    public static final String SUCCESS_VALUE = SUCCESS.name();
    public static final String PARTIAL_FAILURE_VALUE = PARTIAL_FAILURE.name();
    public static final String COMPLETED_WITH_SKIPS_VALUE = COMPLETED_WITH_SKIPS.name();
    public static final String COMPLETED_WITH_NOT_SENT_VALUE = COMPLETED_WITH_NOT_SENT.name();
    public static final String COMPLETED_WITH_FAILURES_VALUE = COMPLETED_WITH_FAILURES.name();
}
