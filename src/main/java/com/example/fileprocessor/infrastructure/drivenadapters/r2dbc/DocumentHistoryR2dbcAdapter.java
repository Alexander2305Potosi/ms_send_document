package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentHistoryMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DocumentHistoryR2dbcAdapter implements DocumentHistoryRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository springDataRepository;

    public DocumentHistoryR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Mono<Void> save(DocumentHistory history) {
        return springDataRepository.save(DocumentHistoryMapper.toEntity(history)).then();
    }

    @Override
    public Flux<DocumentHistory> findByDocumentId(String documentId) {
        return springDataRepository.findByDocumentId(documentId)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Flux<DocumentHistory> findByState(String state) {
        return springDataRepository.findByState(state)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<Void> updateState(String documentId, String state, String errorMessage) {
        return springDataRepository.findByDocumentId(documentId)
            .filter(entity -> entity.getUseCase() == null)
            .next()
            .flatMap(entity -> {
                entity.setState(state);
                entity.setErrorMessage(errorMessage);
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .switchIfEmpty(springDataRepository.findByDocumentId(documentId)
                .next()
                .flatMap(entity -> {
                    entity.setState(state);
                    entity.setErrorMessage(errorMessage);
                    entity.setUpdatedAt(java.time.LocalDateTime.now());
                    return springDataRepository.save(entity);
                }))
            .then();
    }

    @Override
    public Mono<Integer> getRetryCount(String documentId, String useCase) {
        return springDataRepository.findByDocumentIdAndUseCase(documentId, useCase)
            .collectList()
            .map(list -> list.isEmpty() ? 0 : list.get(list.size() - 1).getRetry());
    }
}
