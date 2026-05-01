package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentTraceability;
import com.example.fileprocessor.domain.port.out.DocumentTraceabilityGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentHistoryMapper;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DocumentHistoryR2dbcAdapter implements DocumentTraceabilityGateway {

    private final DocumentHistoryRepository repository;

    public DocumentHistoryR2dbcAdapter(DocumentHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Void> save(DocumentTraceability record) {
        return repository.save(DocumentHistoryMapper.toEntity(record))
            .then();
    }

    @Override
    public Flux<DocumentTraceability> findByProductId(String productId) {
        return repository.findByProductId(productId)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Flux<DocumentTraceability> findByStatus(String status) {
        return repository.findByStatus(status)
            .map(DocumentHistoryMapper::toDomain);
    }
}