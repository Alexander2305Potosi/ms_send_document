package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalDocumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface AnimalDocumentRepository extends R2dbcRepository<AnimalDocumentEntity, Long> {

    @Query("SELECT * FROM esquema_animales.documentos WHERE estado_sincronizacion = $1 AND caso_uso = $2 AND fecha_carga >= $3")
    Flux<AnimalDocumentEntity> findByStateAndUseCaseToday(String estado, String casoUso, LocalDateTime startOfDay);

    @Query("SELECT * FROM esquema_animales.documentos WHERE estado_sincronizacion = ANY($1) AND caso_uso = $2 AND fecha_carga >= $3")
    Flux<AnimalDocumentEntity> findByStatesAndUseCaseToday(String[] estados, String casoUso, LocalDateTime startOfDay);

    @Query("SELECT COUNT(*) > 0 FROM esquema_animales.documentos WHERE id_animal = $1 AND id_documento = $2")
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);
}
