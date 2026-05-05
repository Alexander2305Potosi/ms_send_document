package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {
    Flux<DocumentHistoryEntity> findByDocumentId(String documentId);
    Flux<DocumentHistoryEntity> findByDocumentIdAndUseCase(String documentId, String useCase);
    Flux<DocumentHistoryEntity> findByState(String state);
    Mono<DocumentHistoryEntity> findByDocumentIdAndState(String documentId, String state);
}
