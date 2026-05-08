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
    public Flux<Document> findByStateAndUseCase(String state, String useCase) {
        return springDataRepository.findByEstadoAndCasoUsoOrderByFechaCreacionDesc(state, useCase)
            .map(DocumentMapper::toDomain);
    }

    @Override
    public Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt) {
        return springDataRepository.findById(id)
            .flatMap(entity -> {
                entity.setState(state);
                entity.setUpdatedAt(updatedAt);
                return springDataRepository.save(entity).then();
            })
            .then();
    }

    @Override
    public Mono<Long> updateStateById(Long id, String expectedState, String newState, LocalDateTime updatedAt) {
        return databaseClient
            .sql("UPDATE documentos SET estado = :newState, fecha_actualizacion = :updatedAt WHERE id = :id AND estado = :expectedState")
            .bind("newState", newState)
            .bind("updatedAt", updatedAt)
            .bind("id", id)
            .bind("expectedState", expectedState)
            .fetch()
            .rowsUpdated();
    }
}
