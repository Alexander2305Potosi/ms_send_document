package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsyncOperationStatusTest {

    @Test
    void startLoading_shouldCreateStatusWithLoadOperation() {
        String traceId = "test-trace-123";

        AsyncOperationStatus status = AsyncOperationStatus.startLoading(traceId);

        assertEquals(traceId, status.getTraceId());
        assertEquals(AsyncOperationStatus.OPERATION_LOAD, status.getOperationType());
        assertTrue(status.isSuccess());
    }

    @Test
    void startProcessing_shouldCreateStatusWithProcessOperation() {
        String traceId = "test-trace-456";

        AsyncOperationStatus status = AsyncOperationStatus.startProcessing(traceId);

        assertEquals(traceId, status.getTraceId());
        assertEquals(AsyncOperationStatus.OPERATION_PROCESS, status.getOperationType());
        assertTrue(status.isSuccess());
    }

    @Test
    void withProgress_shouldUpdateCounters() {
        String traceId = "test-trace-789";
        AsyncOperationStatus original = AsyncOperationStatus.startProcessing(traceId);

        AsyncOperationStatus updated = original.withProgress(100, 50, 45, 5);

        assertEquals(100, updated.getTotalItems());
        assertEquals(50, updated.getProcessedItems());
        assertEquals(45, updated.getSuccessItems());
        assertEquals(5, updated.getFailedItems());
    }

    @Test
    void completed_shouldSetCompletedStatus() {
        String traceId = "test-trace-completed";
        AsyncOperationStatus original = AsyncOperationStatus.startProcessing(traceId);
        AsyncOperationStatus withProgress = original.withProgress(10, 10, 8, 2);

        AsyncOperationStatus completed = withProgress.completed();

        assertEquals(AsyncOperationStatus.STATUS_COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
    }
}