package com.example.fileprocessor.domain.usecase;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Result of a document sending operation.
 * Used by both SOAP and S3 implementations.
 */
@Getter
@Builder
public class DocumentResult {
    private final String status;
    private final String message;
    private final String correlationId;
    private final String traceId;
    private final Instant processedAt;
    private final String externalReference;
    private final boolean success;
}