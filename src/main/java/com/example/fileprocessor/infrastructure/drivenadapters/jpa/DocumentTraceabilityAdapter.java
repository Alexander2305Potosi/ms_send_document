package com.example.fileprocessor.infrastructure.drivenadapters.jpa;

import com.example.fileprocessor.domain.entity.DocumentTraceability;
import com.example.fileprocessor.domain.port.out.DocumentTraceabilityGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity.DocumentTraceabilityEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.jpa.repository.DocumentTraceabilityRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class DocumentTraceabilityAdapter implements DocumentTraceabilityGateway {

    private final DocumentTraceabilityRepository repository;

    public DocumentTraceabilityAdapter(DocumentTraceabilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Void> save(DocumentTraceability record) {
        return Mono.fromRunnable(() -> {
            DocumentTraceabilityEntity entity = mapToEntity(record);
            repository.save(entity);
        });
    }

    @Override
    public Flux<DocumentTraceability> findByProductId(String productId) {
        return Flux.fromIterable(repository.findByProductId(productId))
            .map(this::toRecord);
    }

    @Override
    public Flux<DocumentTraceability> findByStatus(String status) {
        return Flux.fromIterable(repository.findByStatus(status))
            .map(this::toRecord);
    }

    private DocumentTraceabilityEntity mapToEntity(DocumentTraceability r) {
        DocumentTraceabilityEntity entity = new DocumentTraceabilityEntity();
        entity.setProductId(r.productId());
        entity.setDocumentId(r.documentId());
        entity.setFilename(r.filename());
        entity.setCompressedFilename(r.compressedFilename());
        entity.setStatus(r.status());
        entity.setErrorCode(r.errorCode());
        entity.setFailureReason(r.failureReason());
        entity.setAttemptCount(r.attemptCount());
        entity.setSentAt(r.sentAt());
        entity.setFailedAt(r.failedAt());
        entity.setCreatedAt(r.createdAt());
        return entity;
    }

    private DocumentTraceability toRecord(DocumentTraceabilityEntity e) {
        return new DocumentTraceability(
            e.getId(),
            e.getProductId(),
            e.getDocumentId(),
            e.getFilename(),
            e.getCompressedFilename(),
            e.getStatus(),
            e.getErrorCode(),
            e.getFailureReason(),
            e.getAttemptCount(),
            e.getSentAt(),
            e.getFailedAt(),
            e.getCreatedAt()
        );
    }
}
