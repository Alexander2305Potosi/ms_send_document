package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.ProductEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface ProductRepository extends R2dbcRepository<ProductEntity, Long> {
    Flux<ProductEntity> findByLoadDateBetween(LocalDateTime start, LocalDateTime end);
    Flux<ProductEntity> findByProductId(String productId);
}