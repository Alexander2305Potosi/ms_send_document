package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;

import java.time.LocalDateTime;

public class DocumentHistoryMapper {

    public static DocumentHistoryEntity toEntity(DocumentHistory domain) {
        return DocumentHistoryEntity.builder()
            .documentId(domain.documentId())
            .productId(domain.productId())
            .useCase(domain.useCase())
            .status(domain.status())
            .errorCode(domain.errorCode())
            .errorMessage(domain.errorMessage())
            .retry(domain.retry())
            .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
            .build();
    }

    public static DocumentHistory toDomain(DocumentHistoryEntity entity) {
        return DocumentHistory.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .productId(entity.getProductId())
            .useCase(entity.getUseCase())
            .status(entity.getStatus())
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .retry(entity.getRetry())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}