package com.example.fileprocessor.domain.entity;

import java.time.LocalDateTime;

public record DocumentHistory(
    Long id,
    String productId,
    String documentId,
    String filename,
    String compressedFilename,
    String status,
    String errorCode,
    String failureReason,
    int attemptCount,
    LocalDateTime sentAt,
    LocalDateTime failedAt,
    LocalDateTime createdAt
) {}