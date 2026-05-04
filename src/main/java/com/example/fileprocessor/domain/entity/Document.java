package com.example.fileprocessor.domain.entity;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record Document(
    Long id,
    String documentId,
    String productId,
    String docKey,
    String name,
    String owner,
    String path,
    String status,
    String versionContract,
    String state,
    String errorMessage,
    Boolean isZip,
    String parentZipName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}