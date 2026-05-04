package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;

public class DocumentHistoryMapper {

    public static DocumentHistoryEntity toEntity(DocumentHistory domain) {
        return DocumentHistoryEntity.builder()
            .productId(domain.productId())
            .documentId(domain.documentId())
            .filename(domain.filename())
            .compressedFilename(domain.compressedFilename())
            .status(domain.status())
            .errorCode(domain.errorCode())
            .failureReason(domain.failureReason())
            .attemptCount(domain.attemptCount())
            .sentAt(domain.sentAt())
            .failedAt(domain.failedAt())
            .createdAt(domain.createdAt())
            .build();
    }

    public static DocumentHistory toDomain(DocumentHistoryEntity entity) {
        return new DocumentHistory(
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