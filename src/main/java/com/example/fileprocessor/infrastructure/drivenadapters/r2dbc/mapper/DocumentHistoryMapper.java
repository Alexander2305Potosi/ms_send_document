package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.DocumentTraceability;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;

public class DocumentHistoryMapper {

    public static DocumentHistoryEntity toEntity(DocumentTraceability domain) {
        DocumentHistoryEntity entity = new DocumentHistoryEntity();
        entity.setProductId(domain.productId());
        entity.setDocumentId(domain.documentId());
        entity.setFilename(domain.filename());
        entity.setCompressedFilename(domain.compressedFilename());
        entity.setStatus(domain.status());
        entity.setErrorCode(domain.errorCode());
        entity.setFailureReason(domain.failureReason());
        entity.setAttemptCount(domain.attemptCount());
        entity.setSentAt(domain.sentAt());
        entity.setFailedAt(domain.failedAt());
        entity.setCreatedAt(domain.createdAt());
        return entity;
    }

    public static DocumentTraceability toDomain(DocumentHistoryEntity entity) {
        return new DocumentTraceability(
            entity.getId(),
            entity.getProductId(),
            entity.getDocumentId(),
            entity.getFilename(),
            entity.getCompressedFilename(),
            entity.getStatus(),
            entity.getErrorCode(),
            entity.getFailureReason(),
            entity.getAttemptCount(),
            entity.getSentAt(),
            entity.getFailedAt(),
            entity.getCreatedAt()
        );
    }
}