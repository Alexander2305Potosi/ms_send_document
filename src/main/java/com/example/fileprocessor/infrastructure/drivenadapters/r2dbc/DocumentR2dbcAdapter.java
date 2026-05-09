package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
    public Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay) {
        return springDataRepository.findByStateAndUseCaseToday(state, useCase, startOfDay)
            .map(DocumentMapper::toDomain);
    }

    @Override
    public Mono<Long> updateStateAndRetry(Long id, String expectedState, String newState,
                                         Integer retryCount, LocalDateTime updatedAt) {
        return springDataRepository.updateStateAndRetry(id, expectedState, newState, retryCount, updatedAt);
    }

    @Override
    public Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId) {
        return springDataRepository.existsByProductIdAndDocumentId(productId, documentId);
    }

    @Override
    public Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold) {
        return springDataRepository.resetStaleDocumentsToday(useCase, startOfDay, threshold, LocalDateTime.now());
    }
}
