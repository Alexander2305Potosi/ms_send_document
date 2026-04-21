package com.example.fileprocessor.domain.entity;

import java.time.Instant;

/**
 * Result of a file upload operation.
 * This is returned by the use case to avoid dependency on infrastructure DTOs.
 */
public record FileUploadResult(
    String status,
    String message,
    String correlationId,
    String traceId,
    Instant processedAt,
    String externalReference,
    boolean success
) {
}
