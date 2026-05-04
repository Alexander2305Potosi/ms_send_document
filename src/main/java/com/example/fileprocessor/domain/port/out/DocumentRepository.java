package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentRepository {
    Mono<Void> save(Document document);
    Mono<Void> updateStatus(String documentId, String status, String errorMessage);
    Mono<Void> updateState(String documentId, String state);
    Flux<Document> findByStatus(String status);
}