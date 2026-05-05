package com.example.fileprocessor.domain.entity;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record DocumentHistory(
    Long id,
    String documentId,
    String productId,
    Boolean active,
    String docKey,
    String name,
    String owner,
    String path,
    String state,
    String versionContract,
    String errorMessage,
    Boolean isZip,
    String parentZipName,
    String useCase,
    String status,
    String errorCode,
    Integer retry,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
