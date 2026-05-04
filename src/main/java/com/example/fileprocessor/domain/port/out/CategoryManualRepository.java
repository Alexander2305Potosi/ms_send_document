package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import reactor.core.publisher.Mono;

public interface CategoryManualRepository {
    Mono<CategoryManualEntity> findByCategoria(String categoria);
}