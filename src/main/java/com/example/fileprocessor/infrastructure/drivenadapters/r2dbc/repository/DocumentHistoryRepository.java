package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {

    @Query("SELECT * FROM historico_documentos WHERE documento_id = :documentoId AND operacion = :operacion ORDER BY fecha_creacion DESC LIMIT 1")
    Mono<DocumentHistoryEntity> findLastByDocumentoIdAndOperacionOrderByFechaCreacionDesc(@Param("documentoId") Long documentoId, @Param("operacion") String operacion);
}
