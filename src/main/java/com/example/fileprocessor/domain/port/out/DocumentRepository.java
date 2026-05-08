package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for managing document metadata.
 */
public interface DocumentRepository {
    Mono<Document> save(Document document);
    Flux<Document> findByStateAndUseCase(String state, String useCase);
}
