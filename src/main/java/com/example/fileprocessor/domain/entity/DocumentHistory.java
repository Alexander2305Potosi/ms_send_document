package com.example.fileprocessor.domain.entity;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record DocumentHistory(
    Long id,
    String documentId,
    String productId,
    String useCase,
    String status,
    String errorCode,
    String errorMessage,
    Integer retry,
    LocalDateTime createdAt
) {}