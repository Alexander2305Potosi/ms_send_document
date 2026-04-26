package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tracks the status of an asynchronous operation.
 * Used to provide async operation status to clients.
 */
@Getter
@Builder
public class AsyncOperationStatus {
    private final String traceId;
    private final String operationType;
    private final String status;
    private final String message;
    private final int totalItems;
    private final int processedItems;
    private final int failedItems;
    private final int successItems;
    private final Instant startedAt;
    private final Instant completedAt;
    private final boolean success;

    public static final String OPERATION_LOAD = "LOAD";
    public static final String OPERATION_PROCESS = "PROCESS";
    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String MSG_LOADING = "Product loading from REST API started";
    public static final String MSG_PROCESSING = "Pending product documents processing started";

    public static AsyncOperationStatus startLoading(String traceId) {
        return AsyncOperationStatus.builder()
            .traceId(traceId)
            .operationType(OPERATION_LOAD)
            .status(STATUS_LOADING)
            .message(MSG_LOADING)
            .startedAt(Instant.now())
            .success(true)
            .build();
    }

    public static AsyncOperationStatus startProcessing(String traceId) {
        return AsyncOperationStatus.builder()
            .traceId(traceId)
            .operationType(OPERATION_PROCESS)
            .status(STATUS_PROCESSING)
            .message(MSG_PROCESSING)
            .startedAt(Instant.now())
            .success(true)
            .build();
    }

    public AsyncOperationStatus withProgress(int total, int processed, int success, int failed) {
        return AsyncOperationStatus.builder()
            .traceId(this.traceId)
            .operationType(this.operationType)
            .status(this.status)
            .message(this.message)
            .totalItems(total)
            .processedItems(processed)
            .successItems(success)
            .failedItems(failed)
            .startedAt(this.startedAt)
            .completedAt(this.completedAt)
            .success(this.success)
            .build();
    }

    public AsyncOperationStatus completed() {
        return AsyncOperationStatus.builder()
            .traceId(this.traceId)
            .operationType(this.operationType)
            .status(STATUS_COMPLETED)
            .message("Operation completed")
            .totalItems(this.totalItems)
            .processedItems(this.processedItems)
            .successItems(this.successItems)
            .failedItems(this.failedItems)
            .startedAt(this.startedAt)
            .completedAt(Instant.now())
            .success(this.failedItems == 0)
            .build();
    }
}