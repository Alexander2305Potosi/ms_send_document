package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;

import java.time.LocalDateTime;

public class DocumentHistoryMapper {

    public static DocumentHistoryEntity toEntity(DocumentHistory domain) {
        return DocumentHistoryEntity.builder()
            .documentId(domain.documentId())
            .filename(domain.filename())
            .operation(domain.operation())
            .messageId(domain.messageId())
            .result(domain.result())
            .errorCode(domain.errorCode())
            .errorMessage(domain.errorMessage())
            .stackTrace(domain.stackTrace())
            .retry(domain.retry() != null ? domain.retry() : 0)
            .startedAt(domain.startedAt())
            .completedAt(domain.completedAt())
            .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
            .build();
    }

    public static DocumentHistory toDomain(DocumentHistoryEntity entity) {
        return DocumentHistory.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .filename(entity.getFilename())
            .operation(entity.getOperation())
            .messageId(entity.getMessageId())
            .result(entity.getResult())
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .stackTrace(entity.getStackTrace())
            .retry(entity.getRetry())
            .startedAt(entity.getStartedAt())
            .completedAt(entity.getCompletedAt())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
