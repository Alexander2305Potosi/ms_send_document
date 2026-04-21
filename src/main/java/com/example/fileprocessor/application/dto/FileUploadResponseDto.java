package com.example.fileprocessor.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadResponseDto(
    String status,
    String message,
    String correlationId,
    String traceId,
    Instant processedAt,
    String externalReference,
    boolean success
) {
}
