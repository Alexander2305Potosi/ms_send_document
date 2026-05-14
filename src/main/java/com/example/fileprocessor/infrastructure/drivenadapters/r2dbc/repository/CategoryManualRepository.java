package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface CategoryManualRepository extends ReactiveCrudRepository<CategoryManualEntity, Long> {
    Mono<CategoryManualEntity> findByCategoria(String categoria);
}
