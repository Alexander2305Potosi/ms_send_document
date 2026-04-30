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
    NOT_SENT,
    DEAD_LETTER;  // Failed after max retries - requires manual intervention

}
