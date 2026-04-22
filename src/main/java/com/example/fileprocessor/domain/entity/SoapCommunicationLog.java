package com.example.fileprocessor.domain.entity;

import java.time.Instant;
import java.util.Objects;

public record SoapCommunicationLog(
    String traceId,
    String status,
    int retryCount,
    String errorCode,
    String filename,
    Instant createdAt
) {
    public SoapCommunicationLog {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
