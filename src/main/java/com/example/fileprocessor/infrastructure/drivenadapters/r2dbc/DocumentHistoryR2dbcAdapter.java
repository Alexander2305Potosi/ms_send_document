package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentHistoryMapper;
import org.springframework.stereotype.Component;
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
    public Mono<DocumentHistory> findLastAudit(Long documentId, String useCase) {
        return springDataRepository.findLastByDocumentoIdAndOperacionOrderByFechaCreacionDesc(documentId, useCase)
            .map(DocumentHistoryMapper::toDomain);
    }
}
