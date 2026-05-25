package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PaisHomologadoRepository extends ReactiveCrudRepository<PaisHomologadoEntity, Long> {
    Flux<PaisHomologadoEntity> findAllByOrderByOrdenAsc();
}
