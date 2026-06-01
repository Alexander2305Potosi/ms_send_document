package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import com.example.fileprocessor.domain.entity.product.StateCount;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

    @Query("SELECT * FROM documentos WHERE estado_sincronizacion = $1 AND caso_uso = $2 AND fecha_carga >= $3")
    Flux<DocumentEntity> findByStateAndUseCaseToday(String estado, String casoUso, LocalDateTime startOfDay);

    @Query("SELECT COUNT(*) > 0 FROM documentos WHERE id_producto = $1 AND id_documento = $2")
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

    // NUEVO
    @Query("SELECT COUNT(*) FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2")
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    // NUEVO
    @Query("SELECT estado_sincronizacion AS state, COUNT(*) AS total FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2 GROUP BY estado_sincronizacion")
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);

    // Obtiene el id_producto del penúltimo registro distinto insertado en el rango (para reanudar de forma segura).
    @Query("SELECT id_producto FROM (SELECT id_producto, MAX(id) AS last_id FROM documentos WHERE fecha_carga BETWEEN $1 AND $2 GROUP BY id_producto) ranked ORDER BY last_id DESC LIMIT 1 OFFSET 1")
    Mono<String> findLastProcessedProductIdInRange(LocalDateTime start, LocalDateTime end);
}
