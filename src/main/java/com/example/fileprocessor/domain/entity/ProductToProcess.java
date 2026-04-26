package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Represents a product to be processed.
 * Products contain multiple documents that may need to be sent via SOAP or S3.
 */
@Getter
@Builder
public class ProductToProcess {
    private final String productId;
    private final String name;
    private final String status;
    private final Instant createdAt;
    private final Instant processedAt;
    private final String traceId;
}
