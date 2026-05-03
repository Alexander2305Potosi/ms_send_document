package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentHistoryMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DocumentHistoryR2dbcAdapter implements DocumentHistoryRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository repository;

    public DocumentHistoryR2dbcAdapter(com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Void> save(DocumentHistory record) {
        return repository.save(DocumentHistoryMapper.toEntity(record))
            .then();
    }

    @Override
    public Flux<DocumentHistory> findByProductId(String productId) {
        return repository.findByProductId(productId)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Flux<DocumentHistory> findByStatus(String status) {
        return repository.findByStatus(status)
            .map(DocumentHistoryMapper::toDomain);
    }
}