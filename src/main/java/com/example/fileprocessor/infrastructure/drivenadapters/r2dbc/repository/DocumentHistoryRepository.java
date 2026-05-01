package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {
    Flux<DocumentHistoryEntity> findByProductId(String productId);
    Flux<DocumentHistoryEntity> findByStatus(String status);
}