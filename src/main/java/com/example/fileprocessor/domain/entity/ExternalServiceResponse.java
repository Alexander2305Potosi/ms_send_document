package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * SOAP response from external service.
 */
@Getter
@Builder
public class ExternalServiceResponse {
    public static final String STATUS_OK = "OK";

    private final String status;
    private final String message;
    private final String correlationId;
    private final String traceId;
    private final Instant processedAt;
    private final String externalReference;

    public boolean isSuccess() {
        return DocumentStatus.SUCCESS.name().equalsIgnoreCase(status) || STATUS_OK.equalsIgnoreCase(status);
    }
}
