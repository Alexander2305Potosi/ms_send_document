package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

        @Query("SELECT * FROM documentos WHERE estado = $1 AND caso_uso = $2 ORDER BY fecha_creacion DESC")
        Flux<DocumentEntity> findByEstadoAndCasoUsoOrderByFechaCreacionDesc(String estado, String casoUso);

        @Query("SELECT * FROM documentos WHERE estado = $1 AND caso_uso = $2 AND fecha_creacion >= $3")
        Flux<DocumentEntity> findByStateAndUseCaseToday(String estado, String casoUso, LocalDateTime startOfDay);


        @Query("SELECT COUNT(*) > 0 FROM documentos WHERE id_producto = $1 AND id_documento = $2")
        Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

}
