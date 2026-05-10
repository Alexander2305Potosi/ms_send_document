package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

    @Query("SELECT * FROM documentos WHERE estado = :estado AND caso_uso = :casoUso ORDER BY fecha_creacion DESC")
    Flux<DocumentEntity> findByEstadoAndCasoUsoOrderByFechaCreacionDesc(@Param("estado") String estado, @Param("casoUso") String casoUso);

    @Query("SELECT * FROM documentos WHERE estado = :estado AND caso_uso = :casoUso AND fecha_creacion >= :startOfDay")
    Flux<DocumentEntity> findByStateAndUseCaseToday(@Param("estado") String estado, @Param("casoUso") String casoUso, @Param("startOfDay") LocalDateTime startOfDay);

    @Modifying
    @Query("UPDATE documentos SET estado = :newState, reintentos = :retryCount, fecha_actualizacion = :updatedAt WHERE id = :id AND estado = :expectedState")
    Mono<Long> updateStateAndRetry(@Param("id") Long id, @Param("expectedState") String expectedState, @Param("newState") String newState, @Param("retryCount") Integer retryCount, @Param("updatedAt") LocalDateTime updatedAt);

    @Query("SELECT COUNT(*) > 0 FROM documentos WHERE id_producto = :productId AND id_documento = :documentId")
    Mono<Boolean> existsByProductIdAndDocumentId(@Param("productId") String productId, @Param("documentId") String documentId);

    @Modifying
    @Query("UPDATE documentos SET estado = 'PENDING', fecha_actualizacion = :now WHERE estado = 'IN_PROGRESS' AND caso_uso = :useCase AND fecha_creacion >= :startOfDay AND fecha_actualizacion < :threshold")
    Mono<Long> resetStaleDocumentsToday(@Param("useCase") String useCase, @Param("startOfDay") LocalDateTime startOfDay, @Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now);
}
