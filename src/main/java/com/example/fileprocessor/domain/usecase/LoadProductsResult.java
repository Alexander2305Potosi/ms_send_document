package com.example.fileprocessor.domain.usecase;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class LoadProductsResult {
    private final String productId;
    private final String name;
    private final int documentCount;
    private final String status;
    private final String message;
    private final String traceId;
    private final Instant processedAt;
    private final boolean success;
}
