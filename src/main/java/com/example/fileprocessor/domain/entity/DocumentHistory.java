package com.example.fileprocessor.domain.entity;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record DocumentHistory(
    Long id,
    Long documentId,
    String filename,
    String operation,
    String messageId,
    String result,
    String errorCode,
    String errorMessage,
    String stackTrace,
    Integer retry,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt
) {}
