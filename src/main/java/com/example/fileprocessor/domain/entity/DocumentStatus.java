package com.example.fileprocessor.domain.entity;

/**
 * Document processing status constants.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SUCCESS,
    FAILURE,
    SKIPPED,
    NOT_SENT;

    public static final String PENDING_VALUE = PENDING.name();
    public static final String PROCESSING_VALUE = PROCESSING.name();
    public static final String RETRY_VALUE = RETRY.name();
    public static final String SUCCESS_VALUE = SUCCESS.name();
    public static final String FAILURE_VALUE = FAILURE.name();
    public static final String SKIPPED_VALUE = SKIPPED.name();
    public static final String NOT_SENT_VALUE = NOT_SENT.name();
}
