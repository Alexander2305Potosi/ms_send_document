package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Result of a file upload operation.
 * This is returned by the use case to avoid dependency on infrastructure DTOs.
 */
@Getter
@Builder
public class FileUploadResult {
    private final String status;
    private final String message;
    private final String correlationId;
    private final String traceId;
    private final Instant processedAt;
    private final String externalReference;
    private final boolean success;
}