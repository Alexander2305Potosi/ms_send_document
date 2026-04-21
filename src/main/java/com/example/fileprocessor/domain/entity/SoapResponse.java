package com.example.fileprocessor.domain.entity;

import java.time.Instant;
import java.util.Objects;

public record SoapResponse(
    String status,
    String message,
    String correlationId,
    String traceId,
    Instant processedAt,
    String externalReference
) {
    public SoapResponse {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status);
    }
}
