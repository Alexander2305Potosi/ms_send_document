package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Document pending to be downloaded from REST API and processed.
 */
@Getter
@Builder
public class DocumentToProcess {
    private final String documentId;
    private final String filename;
    private final String origin;
    private final String status;
    private final Instant createdAt;
    private final Instant processedAt;
    private final String traceId;
    private final String soapCorrelationId;
    private final String errorCode;
}
