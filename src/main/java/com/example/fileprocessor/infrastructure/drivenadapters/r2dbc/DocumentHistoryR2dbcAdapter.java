package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentHistoryMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
    public Flux<DocumentHistory> findByStateAndUseCase(String state, String useCase) {
        return springDataRepository.findByStateAndUseCase(state, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Flux<DocumentHistory> findByDocumentIdAndStateAndUseCase(String documentId, String state, String useCase) {
        return springDataRepository.findByDocumentIdAndStateAndUseCase(documentId, state, useCase)
            .flux()
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<Void> updateStateAndUseCase(String documentId, String state, String useCase) {
        return springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc(documentId, useCase)
            .flatMap(entity -> {
                entity.setState(state);
                entity.setUpdatedAt(LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<Void> updateWithAudit(String documentId, String state, String errorCode, String errorMessage, int retry, String useCase, String stackTrace, LocalDateTime completedAt) {
        return springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc(documentId, useCase)
            .flatMap(entity -> {
                entity.setState(state);
                entity.setErrorCode(errorCode);
                entity.setErrorMessage(errorMessage);
                entity.setRetry(retry);
                entity.setUseCase(useCase);
                entity.setStackTrace(stackTrace);
                entity.setCompletedAt(completedAt);
                entity.setUpdatedAt(LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<DocumentHistory> findLastAudit(String documentId, String useCase) {
        return springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc(documentId, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }
}