package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Log entry for communication tracking (SOAP, S3, etc.).
 * Includes documentId for audit traceability.
 */
@Getter
@Builder
public class CommunicationLog {
    private final String traceId;
    private final String documentId;
    private final String status;
    private final int retryCount;
    private final String errorCode;
    private final String filename;
    private final Instant createdAt;
}
