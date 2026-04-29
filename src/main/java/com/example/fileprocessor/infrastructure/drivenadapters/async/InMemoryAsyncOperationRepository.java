package com.example.fileprocessor.infrastructure.drivenadapters.async;

import com.example.fileprocessor.domain.entity.AsyncOperationStatus;
import com.example.fileprocessor.domain.port.out.AsyncOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AsyncOperationRepository.
 * Uses ConcurrentHashMap with atomic operations for thread-safe reactive operations.
 */
@Component
public class InMemoryAsyncOperationRepository implements AsyncOperationRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAsyncOperationRepository.class);

    private final Map<String, AsyncOperationStatus> operations = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(AsyncOperationStatus status) {
        return Mono.fromRunnable(() -> {
            operations.put(status.getTraceId(), status);
            log.info("Async operation saved: traceId={}, operationType={}, status={}",
                status.getTraceId(), status.getOperationType(), status.getStatus());
        });
    }

    @Override
    public Mono<Void> updateProgress(String traceId, int processed, int success, int failed) {
        return Mono.defer(() -> {
            AsyncOperationStatus current = operations.get(traceId);
            if (current == null) {
                return Mono.error(new IllegalStateException(
                    "Cannot update progress - operation not found: traceId=" + traceId));
            }
            AsyncOperationStatus updated = current.withProgress(
                current.getTotalItems() > 0 ? current.getTotalItems() : processed,
                processed,
                success,
                failed
            );
            operations.put(traceId, updated);
            log.debug("Progress updated: traceId={}, processed={}, success={}, failed={}",
                traceId, processed, success, failed);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> incrementProgress(String traceId, boolean success) {
        return Mono.defer(() -> {
            AsyncOperationStatus current = operations.get(traceId);
            if (current == null) {
                return Mono.error(new IllegalStateException(
                    "Cannot increment progress - operation not found: traceId=" + traceId));
            }
            int newProcessed = current.getProcessedItems() + 1;
            int newSuccess = current.getSuccessItems() + (success ? 1 : 0);
            int newFailed = current.getFailedItems() + (success ? 0 : 1);
            AsyncOperationStatus updated = current.withProgress(
                current.getTotalItems() > 0 ? current.getTotalItems() : newProcessed,
                newProcessed,
                newSuccess,
                newFailed
            );
            operations.put(traceId, updated);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> markCompleted(String traceId) {
        return Mono.defer(() -> {
            AsyncOperationStatus current = operations.get(traceId);
            if (current == null) {
                return Mono.error(new IllegalStateException(
                    "Cannot mark completed - operation not found: traceId=" + traceId));
            }
            operations.put(traceId, current.completed());
            log.info("Async operation completed: traceId={}", traceId);
            return Mono.empty();
        });
    }

    @Override
    public Mono<AsyncOperationStatus> findByTraceId(String traceId) {
        return Mono.defer(() -> Mono.justOrEmpty(operations.get(traceId)));
    }
}