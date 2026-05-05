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
    public Flux<DocumentHistory> findByStateAndUseCase(String state, String useCase) {
        return springDataRepository.findByStateAndUseCase(state, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Flux<DocumentHistory> findByDocumentIdAndStateAndUseCase(String documentId, String state, String useCase) {
        return springDataRepository.findByDocumentIdAndStateAndUseCase(documentId, state, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<Void> updateState(String documentId, String state, String errorMessage) {
        return springDataRepository.findByDocumentId(documentId)
            .next()
            .flatMap(entity -> {
                entity.setState(state);
                entity.setErrorMessage(errorMessage);
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<Void> updateStateAndUseCase(String documentId, String state, String useCase) {
        return springDataRepository.findByDocumentIdAndStateAndUseCase(documentId, state, useCase)
            .next()
            .flatMap(entity -> {
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<Void> updateWithAudit(String documentId, String state, String errorCode, String errorMessage, int retry, String useCase) {
        return springDataRepository.findByDocumentId(documentId)
            .next()
            .flatMap(entity -> {
                entity.setState(state);
                entity.setErrorCode(errorCode);
                entity.setErrorMessage(errorMessage);
                entity.setRetry(retry);
                entity.setUseCase(useCase);
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<DocumentHistory> findLastAudit(String documentId) {
        return springDataRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<DocumentHistory> findLastAudit(String documentId, String useCase) {
        return springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc(documentId, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<Integer> getRetryCount(String documentId, String useCase) {
        return springDataRepository.findByDocumentIdAndUseCase(documentId, useCase)
            .collectList()
            .map(list -> list.isEmpty() ? 0 : list.get(list.size() - 1).getRetry());
    }
}
