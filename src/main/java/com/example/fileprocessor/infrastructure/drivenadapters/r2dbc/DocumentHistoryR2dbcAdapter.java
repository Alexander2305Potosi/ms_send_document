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
        return springDataRepository.findByEstadoAndCasoUsoOrderByFechaCreacionDesc(state, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<DocumentHistory> findLastAudit(String documentId, String useCase) {
        return springDataRepository.findLastByDocumentIdAndCasoUsoOrderByFechaCreacionDesc(documentId, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt) {
        return springDataRepository.updateStateById(id, state, updatedAt);
    }

    @Override
    public Mono<Void> updateWithAuditById(Long id, String state, String errorCode, String errorMessage,
                                         int retry, String stackTrace, LocalDateTime completedAt, LocalDateTime updatedAt) {
        return springDataRepository.updateWithAuditById(id, state, errorCode, errorMessage, retry, stackTrace, completedAt, updatedAt);
    }
}
