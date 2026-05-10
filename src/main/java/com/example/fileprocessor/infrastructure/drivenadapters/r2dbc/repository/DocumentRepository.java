package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

    @Query("SELECT * FROM documentos WHERE estado = $1 AND caso_uso = $2 ORDER BY fecha_creacion DESC")
    Flux<DocumentEntity> findByEstadoAndCasoUsoOrderByFechaCreacionDesc(String estado, String casoUso);

    @Query("SELECT * FROM documentos WHERE estado = $1 AND caso_uso = $2 AND fecha_creacion >= $3")
    Flux<DocumentEntity> findByStateAndUseCaseToday(String estado, String casoUso, LocalDateTime startOfDay);

    @Modifying
    @Query("UPDATE documentos SET estado = $1, reintentos = $2, fecha_actualizacion = $3 WHERE id = $4 AND estado = $5")
    Mono<Long> updateStateAndRetry(String newState, Integer retryCount, LocalDateTime updatedAt, Long id, String expectedState);

    @Query("SELECT COUNT(*) > 0 FROM documentos WHERE id_producto = $1 AND id_documento = $2")
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

    @Modifying
    @Query("UPDATE documentos SET estado = 'PENDING', fecha_actualizacion = $1 WHERE estado = 'IN_PROGRESS' AND caso_uso = $2 AND fecha_creacion >= $3 AND fecha_actualizacion < $4")
    Mono<Long> resetStaleDocumentsToday(LocalDateTime now, String useCase, LocalDateTime startOfDay, LocalDateTime threshold);
}
