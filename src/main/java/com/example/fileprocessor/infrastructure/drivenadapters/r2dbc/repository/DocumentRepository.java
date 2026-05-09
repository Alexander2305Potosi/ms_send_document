package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

    @Query("SELECT * FROM documentos WHERE estado = :estado AND caso_uso = :casoUso ORDER BY fecha_creacion DESC")
    Flux<DocumentEntity> findByEstadoAndCasoUsoOrderByFechaCreacionDesc(String estado, String casoUso);

    @Query("SELECT * FROM documentos WHERE estado = :estado AND caso_uso = :casoUso AND fecha_creacion >= :startOfDay")
    Flux<DocumentEntity> findByStateAndUseCaseToday(String estado, String casoUso, LocalDateTime startOfDay);

    @Query("UPDATE documentos SET estado = :newState, reintentos = :retryCount, fecha_actualizacion = :updatedAt WHERE id = :id AND estado = :expectedState")
    Mono<Long> updateStateAndRetry(Long id, String expectedState, String newState, Integer retryCount, LocalDateTime updatedAt);

    @Query("SELECT COUNT(*) > 0 FROM documentos WHERE id_producto = :productId AND id_documento = :documentId")
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

    @Query("UPDATE documentos SET estado = 'PENDING', fecha_actualizacion = :now WHERE estado = 'IN_PROGRESS' AND caso_uso = :useCase AND fecha_creacion >= :startOfDay AND fecha_actualizacion < :threshold")
    Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold, LocalDateTime now);
}
