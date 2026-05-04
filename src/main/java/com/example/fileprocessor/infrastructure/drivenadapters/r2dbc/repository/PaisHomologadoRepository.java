package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface PaisHomologadoRepository extends R2dbcRepository<PaisHomologadoEntity, Long> {
    Flux<PaisHomologadoEntity> findByPais(String pais);
}