package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ProductDocumentToProcess {
    private final String documentId;
    private final String productId;
    private final String parentDocumentId;
    private final String filename;
    private final byte[] content;
    private final String contentType;
    private final String origin;
    private final String status;
    private final Instant createdAt;
    private final Instant processedAt;
    private final String traceId;
    private final String soapCorrelationId;
    private final String errorCode;
}
