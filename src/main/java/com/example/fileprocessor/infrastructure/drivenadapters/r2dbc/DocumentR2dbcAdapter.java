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
    public Mono<Void> save(Document document) {
        return springDataRepository.save(DocumentMapper.toEntity(document)).then();
    }

    @Override
    public Mono<Void> updateState(String documentId, String state, String errorMessage) {
        return springDataRepository.findByDocumentId(documentId)
            .flatMap(entity -> {
                entity.setState(state);
                entity.setErrorMessage(errorMessage);
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Flux<Document> findByState(String state) {
        return springDataRepository.findByState(state)
            .map(DocumentMapper::toDomain);
    }
}