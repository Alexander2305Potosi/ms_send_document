package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {

    @Query("SELECT * FROM historico_documentos WHERE estado = :estado AND caso_uso = :casoUso ORDER BY fecha_creacion DESC")
    Flux<DocumentHistoryEntity> findByEstadoAndCasoUsoOrderByFechaCreacionDesc(String estado, String casoUso);

    @Query("SELECT * FROM historico_documentos WHERE id_documento = :documentId AND caso_uso = :casoUso ORDER BY fecha_creacion DESC LIMIT 1")
    Mono<DocumentHistoryEntity> findLastByDocumentIdAndCasoUsoOrderByFechaCreacionDesc(String documentId, String casoUso);
}
