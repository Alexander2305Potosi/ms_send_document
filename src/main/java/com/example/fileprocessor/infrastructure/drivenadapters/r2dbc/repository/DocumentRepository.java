package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {
    Flux<DocumentEntity> findByStatus(String status);
    Mono<DocumentEntity> findByDocumentId(String documentId);
}