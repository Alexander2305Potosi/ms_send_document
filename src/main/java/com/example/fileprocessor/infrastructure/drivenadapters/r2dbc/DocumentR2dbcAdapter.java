package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DocumentR2dbcAdapter implements DocumentRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository springDataRepository;

    public DocumentR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
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
}
