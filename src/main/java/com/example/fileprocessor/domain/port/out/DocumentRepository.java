package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface DocumentRepository {
    Mono<Document> save(Document document);
    Flux<Document> findByStateAndUseCase(String state, String useCase);
    Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt);
    Mono<Long> updateStateById(Long id, String expectedState, String newState, LocalDateTime updatedAt);
}
