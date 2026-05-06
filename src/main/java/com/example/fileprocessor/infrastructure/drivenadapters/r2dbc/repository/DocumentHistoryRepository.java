package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {

    @Query("SELECT * FROM historico_documentos WHERE estado = :state AND caso_uso = :useCase ORDER BY fecha_creacion DESC")
    Flux<DocumentHistoryEntity> findByStateAndUseCase(String state, String useCase);

    @Query("SELECT * FROM historico_documentos WHERE id_documento = :documentId AND caso_uso = :useCase ORDER BY fecha_creacion DESC LIMIT 1")
    Mono<DocumentHistoryEntity> findLastAudit(String documentId, String useCase);

    @Modifying
    @Query("UPDATE historico_documentos SET estado = :state, fecha_actualizacion = :updatedAt WHERE id = :id")
    Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE historico_documentos SET estado = :state, codigo_error = :errorCode, mensaje_error = :errorMessage, " +
           "reintentos = :retry, stack_trace = :stackTrace, fecha_fin = :completedAt, fecha_actualizacion = :updatedAt " +
           "WHERE id = :id")
    Mono<Void> updateWithAuditById(Long id, String state, String errorCode, String errorMessage, int retry,
                                   String stackTrace, LocalDateTime completedAt, LocalDateTime updatedAt);
}
