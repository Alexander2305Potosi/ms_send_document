package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.BaseDocument;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Generic persistence gateway orchestrating document lifecycle and audit trail.
 */
public interface PersistenceGateway<T extends BaseDocument, H extends BaseDocumentHistoryDTO> {
    Flux<T> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay);
    
    Mono<Long> lockDocumentForProcessing(T doc, int currentRetryCount);

    Mono<Void> finalizeProcessingAtomically(H history);
    
    Mono<Void> saveHistory(H history);
}
