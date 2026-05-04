package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CountryHomologatedEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface CountryHomologatedRepository extends R2dbcRepository<CountryHomologatedEntity, Long> {
    Flux<CountryHomologatedEntity> findByCountry(String country);
}