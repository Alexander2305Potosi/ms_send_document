package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class DocumentR2dbcAdapter implements DocumentRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository springDataRepository;
    private final DatabaseClient databaseClient;

    public DocumentR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository springDataRepository,
            DatabaseClient databaseClient) {
        this.springDataRepository = springDataRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Document> save(Document document) {
        return springDataRepository.save(DocumentMapper.toEntity(document))
            .map(DocumentMapper::toDomain);
    }

    @Override
    public Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay) {
        // Usamos DatabaseClient para tener control total sobre el filtro de fecha de creación
        return databaseClient
            .sql("SELECT * FROM documentos WHERE estado = :state AND caso_uso = :useCase AND fecha_creacion >= :startOfDay")
            .bind("state", state)
            .bind("useCase", useCase)
            .bind("startOfDay", startOfDay)
            .map((row, metadata) -> {
                // Mapeo manual simple o delegación si es posible
                return Document.builder()
                    .id(row.get("id", Long.class))
                    .documentId(row.get("id_documento", String.class))
                    .productId(row.get("id_producto", String.class))
                    .name(row.get("nombre", String.class))
                    .state(row.get("estado", String.class))
                    .useCase(row.get("caso_uso", String.class))
                    .retryCount(row.get("reintentos", Integer.class))
                    .build();
            })
            .all();
    }

    @Override
    public Mono<Long> updateStateAndRetry(Long id, String expectedState, String newState, 
                                         Integer retryCount, LocalDateTime updatedAt) {
        return databaseClient
            .sql("UPDATE documentos SET estado = :newState, reintentos = :retryCount, fecha_actualizacion = :updatedAt " +
                 "WHERE id = :id AND estado = :expectedState")
            .bind("newState", newState)
            .bind("retryCount", retryCount != null ? retryCount : 0)
            .bind("updatedAt", updatedAt)
            .bind("id", id)
            .bind("expectedState", expectedState)
            .fetch()
            .rowsUpdated();
    }

    @Override
    public Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold) {
        return databaseClient
            .sql("UPDATE documentos SET estado = 'PENDING', fecha_actualizacion = :now " +
                 "WHERE estado = 'IN_PROGRESS' AND caso_uso = :useCase " +
                 "AND fecha_creacion >= :startOfDay AND fecha_actualizacion < :threshold")
            .bind("now", LocalDateTime.now())
            .bind("useCase", useCase)
            .bind("startOfDay", startOfDay)
            .bind("threshold", threshold)
            .fetch()
            .rowsUpdated();
    }
}
