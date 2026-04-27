package com.example.fileprocessor.infrastructure.drivenadapters.async;

import com.example.fileprocessor.domain.entity.AsyncOperationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

        StepVerifier.create(repository.save(status))
            .verifyComplete();

        StepVerifier.create(repository.findByTraceId("trace-1"))
            .assertNext(found -> {
                assertEquals("trace-1", found.getTraceId());
                assertEquals(AsyncOperationStatus.OPERATION_LOAD, found.getOperationType());
            })
            .verifyComplete();
    }

    @Test
    void updateProgress_shouldUpdateCounters() {
        AsyncOperationStatus status = AsyncOperationStatus.startProcessing("trace-2");

        StepVerifier.create(repository.save(status)
            .then(repository.updateProgress("trace-2", 10, 8, 2)))
            .verifyComplete();

        StepVerifier.create(repository.findByTraceId("trace-2"))
            .assertNext(updated -> {
                assertEquals(10, updated.getProcessedItems());
                assertEquals(8, updated.getSuccessItems());
                assertEquals(2, updated.getFailedItems());
            })
            .verifyComplete();
    }

    @Test
    void markCompleted_shouldSetCompletedStatus() {
        AsyncOperationStatus status = AsyncOperationStatus.startProcessing("trace-3");

        StepVerifier.create(repository.save(status)
            .then(repository.markCompleted("trace-3")))
            .verifyComplete();

        StepVerifier.create(repository.findByTraceId("trace-3"))
            .assertNext(completed -> {
                assertEquals(AsyncOperationStatus.STATUS_COMPLETED, completed.getStatus());
                assertNotNull(completed.getCompletedAt());
            })
            .verifyComplete();
    }

    @Test
    void findByTraceId_withNonExistent_shouldReturnEmpty() {
        StepVerifier.create(repository.findByTraceId("non-existent"))
            .verifyComplete();
    }

    @Test
    void multipleOperations_shouldBeIndependent() {
        AsyncOperationStatus status1 = AsyncOperationStatus.startLoading("trace-a");
        AsyncOperationStatus status2 = AsyncOperationStatus.startProcessing("trace-b");

        StepVerifier.create(
            Mono.zip(
                repository.save(status1),
                repository.save(status2)
            )
        ).verifyComplete();

        StepVerifier.create(repository.findByTraceId("trace-a"))
            .assertNext(found -> assertNotNull(found))
            .verifyComplete();

        StepVerifier.create(repository.findByTraceId("trace-b"))
            .assertNext(found -> assertNotNull(found))
            .verifyComplete();

        StepVerifier.create(repository.findByTraceId("trace-c"))
            .verifyComplete();
    }

    @Test
    void updateProgress_withNonExistentTraceId_shouldNotFail() {
        StepVerifier.create(repository.updateProgress("non-existent", 10, 5, 5))
            .verifyComplete();
    }
}