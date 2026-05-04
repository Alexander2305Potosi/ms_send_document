package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface CategoryManualRepository extends R2dbcRepository<CategoryManualEntity, Long> {
    Mono<CategoryManualEntity> findByCategoriaAndFechaVigenciaGreaterThanEqual(String categoria, LocalDate fechaVigencia);
    Flux<CategoryManualEntity> findByFechaVigenciaGreaterThanEqual(LocalDate fechaVigencia);
}