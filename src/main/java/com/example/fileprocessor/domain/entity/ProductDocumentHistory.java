package com.example.fileprocessor.domain.entity;

import lombok.Builder;

@Builder
public record ProductDocumentHistory(
    String productId,
    boolean isZip,
    String pais,
    Long id,
    String documentId,
    Boolean active,
    String docKey,
    String name,
    String owner,
    String path,
    String status,
    String versionContract,
    String state,
    String errorMessage,
    String filename,
    String contentType,
    Long size,
    String origin,
    byte[] content,
    String parentZipName
) {}