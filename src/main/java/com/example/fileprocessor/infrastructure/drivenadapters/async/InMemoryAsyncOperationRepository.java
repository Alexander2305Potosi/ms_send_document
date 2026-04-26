package com.example.fileprocessor.infrastructure.drivenadapters.async;

import com.example.fileprocessor.domain.entity.AsyncOperationStatus;
import com.example.fileprocessor.domain.port.out.AsyncOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AsyncOperationRepository.
 * Uses ConcurrentHashMap for thread-safe operations.
 */
@Component
public class InMemoryAsyncOperationRepository implements AsyncOperationRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAsyncOperationRepository.class);

    private final Map<String, AsyncOperationStatus> operations = new ConcurrentHashMap<>();

    @Override
    public void save(AsyncOperationStatus status) {
        operations.put(status.getTraceId(), status);
        log.info("Async operation saved: traceId={}, operationType={}, status={}",
            status.getTraceId(), status.getOperationType(), status.getStatus());
    }

    @Override
    public void updateProgress(String traceId, int processed, int success, int failed) {
        AsyncOperationStatus current = operations.get(traceId);
        if (current != null) {
            AsyncOperationStatus updated = current.withProgress(
                current.getTotalItems() > 0 ? current.getTotalItems() : processed,
                processed,
                success,
                failed
            );
            operations.put(traceId, updated);
            log.debug("Progress updated: traceId={}, processed={}, success={}, failed={}",
                traceId, processed, success, failed);
        }
    }

    @Override
    public void markCompleted(String traceId) {
        AsyncOperationStatus current = operations.get(traceId);
        if (current != null) {
            AsyncOperationStatus completed = current.completed();
            operations.put(traceId, completed);
            log.info("Async operation completed: traceId={}, success={}, failed={}",
                traceId, completed.getSuccessItems(), completed.getFailedItems());
        }
    }

    @Override
    public AsyncOperationStatus findByTraceId(String traceId) {
        return operations.get(traceId);
    }
}