package com.example.fileprocessor.domain.entity;

import lombok.Builder;

@Builder
public record ProductDocumentFile(
    String productId,
    String documentId,
    String filename,
    byte[] content,
    String contentType,
    long size,
    boolean isZip,
    String origin,
    String pais
) {}