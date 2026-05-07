package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

    @Query("SELECT * FROM documentos WHERE estado = :estado AND caso_uso = :casoUso ORDER BY fecha_creacion DESC")
    Flux<DocumentEntity> findByEstadoAndCasoUsoOrderByFechaCreacionDesc(String estado, String casoUso);
}
