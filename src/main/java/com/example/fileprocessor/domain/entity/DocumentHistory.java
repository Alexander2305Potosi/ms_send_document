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
    String errorCode,
    Integer retry,
    String operation,
    String messageId,
    String stackTrace,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
