package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Log entry for SOAP communication tracking.
 */
@Getter
@Builder
public class SoapCommunicationLog {
    private final String traceId;
    private final String status;
    private final int retryCount;
    private final String errorCode;
    private final String filename;
    private final Instant createdAt;
}