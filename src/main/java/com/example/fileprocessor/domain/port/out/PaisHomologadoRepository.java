package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import reactor.core.publisher.Mono;

public interface PaisHomologadoRepository {
    Mono<PaisHomologadoEntity> findByPais(String pais);
}