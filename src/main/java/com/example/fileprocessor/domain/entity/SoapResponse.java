package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * SOAP response from external service.
 */
@Getter
@Builder
public class SoapResponse {
    private final String status;
    private final String message;
    private final String correlationId;
    private final String traceId;
    private final Instant processedAt;
    private final String externalReference;

    public boolean isSuccess() {
        return DocumentStatus.SUCCESS_VALUE.equalsIgnoreCase(status) || ApiConstants.SOAP_STATUS_OK.equalsIgnoreCase(status);
    }
}
