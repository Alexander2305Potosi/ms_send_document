package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistoryDTO;
import reactor.core.publisher.Mono;

/**
 * Port for persisting document processing history.
 */
public interface DocumentHistoryRepository {
    /**
     * Records the outcome of a document processing attempt.
     */
    Mono<Void> saveHistory(Document doc, DocumentHistoryDTO history);
}
