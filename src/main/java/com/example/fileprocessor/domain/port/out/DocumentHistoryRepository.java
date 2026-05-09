package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Port for persisting document processing history.
 */
public interface DocumentHistoryRepository {
    /**
     * Records the outcome of a document processing attempt.
     */
    Mono<Void> saveHistory(Long docId, String filename, String operation, 
                           FileUploadResponse response, Instant startTime);
}
