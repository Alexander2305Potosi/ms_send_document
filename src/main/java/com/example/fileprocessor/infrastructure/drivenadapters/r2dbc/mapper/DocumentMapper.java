package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;

import java.time.LocalDateTime;

public class DocumentMapper {

    public static DocumentEntity toEntity(Document domain) {
        return DocumentEntity.builder()
            .documentId(domain.documentId())
            .productId(domain.productId())
            .active(true)
            .docKey(domain.docKey())
            .name(domain.name())
            .owner(domain.owner())
            .path(domain.path())
            .state(domain.state())
            .versionContract(domain.versionContract())
            .errorMessage(domain.errorMessage())
            .isZip(domain.isZip())
            .parentZipName(domain.parentZipName())
            .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
            .updatedAt(domain.updatedAt() != null ? domain.updatedAt() : LocalDateTime.now())
            .build();
    }

    public static Document toDomain(DocumentEntity entity) {
        return Document.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .productId(entity.getProductId())
            .docKey(entity.getDocKey())
            .name(entity.getName())
            .owner(entity.getOwner())
            .path(entity.getPath())
            .state(entity.getState())
            .versionContract(entity.getVersionContract())
            .errorMessage(entity.getErrorMessage())
            .isZip(entity.getIsZip())
            .parentZipName(entity.getParentZipName())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}