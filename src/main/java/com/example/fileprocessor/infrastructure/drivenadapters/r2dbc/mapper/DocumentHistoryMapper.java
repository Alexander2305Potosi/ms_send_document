package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;

import java.time.LocalDateTime;

public class DocumentHistoryMapper {

    public static DocumentHistoryEntity toEntity(DocumentHistory domain) {
        return DocumentHistoryEntity.builder()
            .documentId(domain.documentId())
            .productId(domain.productId())
            .active(domain.active() != null ? domain.active() : true)
            .docKey(domain.docKey())
            .name(domain.name())
            .owner(domain.owner())
            .path(domain.path())
            .state(domain.state())
            .versionContract(domain.versionContract())
            .errorMessage(domain.errorMessage())
            .isZip(domain.isZip() != null ? domain.isZip() : false)
            .parentZipName(domain.parentZipName())
            .useCase(domain.useCase())
            .errorCode(domain.errorCode())
            .retry(domain.retry() != null ? domain.retry() : 0)
            .operation(domain.operation())
            .messageId(domain.messageId())
            .stackTrace(domain.stackTrace())
            .startedAt(domain.startedAt())
            .completedAt(domain.completedAt())
            .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
            .updatedAt(domain.updatedAt() != null ? domain.updatedAt() : LocalDateTime.now())
            .build();
    }

    public static DocumentHistory toDomain(DocumentHistoryEntity entity) {
        return DocumentHistory.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .productId(entity.getProductId())
            .active(entity.getActive())
            .docKey(entity.getDocKey())
            .name(entity.getName())
            .owner(entity.getOwner())
            .path(entity.getPath())
            .state(entity.getState())
            .versionContract(entity.getVersionContract())
            .errorMessage(entity.getErrorMessage())
            .isZip(entity.getIsZip())
            .parentZipName(entity.getParentZipName())
            .useCase(entity.getUseCase())
            .errorCode(entity.getErrorCode())
            .retry(entity.getRetry())
            .operation(entity.getOperation())
            .messageId(entity.getMessageId())
            .stackTrace(entity.getStackTrace())
            .startedAt(entity.getStartedAt())
            .completedAt(entity.getCompletedAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
