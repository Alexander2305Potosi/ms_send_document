package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

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
