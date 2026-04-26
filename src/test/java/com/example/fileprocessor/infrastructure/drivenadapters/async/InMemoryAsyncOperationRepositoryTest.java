package com.example.fileprocessor.infrastructure.drivenadapters.async;

import com.example.fileprocessor.domain.entity.AsyncOperationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAsyncOperationRepositoryTest {

    private InMemoryAsyncOperationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAsyncOperationRepository();
    }

    @Test
    void save_shouldStoreOperationStatus() {
        AsyncOperationStatus status = AsyncOperationStatus.startLoading("trace-1");

        repository.save(status);

        AsyncOperationStatus found = repository.findByTraceId("trace-1");
        assertNotNull(found);
        assertEquals("trace-1", found.getTraceId());
        assertEquals(AsyncOperationStatus.OPERATION_LOAD, found.getOperationType());
    }

    @Test
    void updateProgress_shouldUpdateCounters() {
        AsyncOperationStatus status = AsyncOperationStatus.startProcessing("trace-2");
        repository.save(status);

        repository.updateProgress("trace-2", 10, 8, 2);

        AsyncOperationStatus updated = repository.findByTraceId("trace-2");
        assertEquals(10, updated.getProcessedItems());
        assertEquals(8, updated.getSuccessItems());
        assertEquals(2, updated.getFailedItems());
    }

    @Test
    void markCompleted_shouldSetCompletedStatus() {
        AsyncOperationStatus status = AsyncOperationStatus.startProcessing("trace-3");
        repository.save(status);

        repository.markCompleted("trace-3");

        AsyncOperationStatus completed = repository.findByTraceId("trace-3");
        assertEquals(AsyncOperationStatus.STATUS_COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
    }

    @Test
    void findByTraceId_withNonExistent_shouldReturnNull() {
        AsyncOperationStatus found = repository.findByTraceId("non-existent");

        assertNull(found);
    }

    @Test
    void multipleOperations_shouldBeIndependent() {
        AsyncOperationStatus status1 = AsyncOperationStatus.startLoading("trace-a");
        AsyncOperationStatus status2 = AsyncOperationStatus.startProcessing("trace-b");

        repository.save(status1);
        repository.save(status2);

        assertNotNull(repository.findByTraceId("trace-a"));
        assertNotNull(repository.findByTraceId("trace-b"));
        assertNull(repository.findByTraceId("trace-c"));
    }
}