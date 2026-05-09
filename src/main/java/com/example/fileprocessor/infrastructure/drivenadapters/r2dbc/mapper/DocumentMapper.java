package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;

public class DocumentMapper {

    public static Document toDomain(DocumentEntity entity) {
        if (entity == null) return null;
        return Document.builder()
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
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .retryCount(entity.getRetryCount())
            .build();
    }

    public static DocumentEntity toEntity(Document domain) {
        if (domain == null) return null;
        return DocumentEntity.builder()
            .id(domain.id())
            .documentId(domain.documentId())
            .productId(domain.productId())
            .active(domain.active())
            .docKey(domain.docKey())
            .name(domain.name())
            .owner(domain.owner())
            .path(domain.path())
            .state(domain.state())
            .versionContract(domain.versionContract())
            .errorMessage(domain.errorMessage())
            .isZip(domain.isZip())
            .parentZipName(domain.parentZipName())
            .useCase(domain.useCase())
            .createdAt(domain.createdAt())
            .updatedAt(domain.updatedAt())
            .retryCount(domain.retryCount())
            .build();
    }
}
