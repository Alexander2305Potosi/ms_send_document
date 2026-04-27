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
        return Mono.fromRunnable(() -> {
            operations.compute(traceId, (key, current) -> {
                if (current == null) {
                    log.warn("Cannot update progress - operation not found: traceId={}", traceId);
                    return null;
                }
                return current.withProgress(
                    current.getTotalItems() > 0 ? current.getTotalItems() : processed,
                    processed,
                    success,
                    failed
                );
            });
            log.debug("Progress updated: traceId={}, processed={}, success={}, failed={}",
                traceId, processed, success, failed);
        });
    }

    @Override
    public Mono<Void> markCompleted(String traceId) {
        return Mono.fromRunnable(() -> {
            operations.compute(traceId, (key, current) -> {
                if (current == null) {
                    log.warn("Cannot mark completed - operation not found: traceId={}", traceId);
                    return null;
                }
                return current.completed();
            });
            log.info("Async operation completed: traceId={}", traceId);
        });
    }

    @Override
    public Mono<AsyncOperationStatus> findByTraceId(String traceId) {
        return Mono.justOrEmpty(operations.get(traceId));
    }
}