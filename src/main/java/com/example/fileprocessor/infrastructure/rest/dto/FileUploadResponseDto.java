package com.example.fileprocessor.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
public class FileUploadResponseDto {
    private final String status;
    private final String message;
    private final String correlationId;
    private final String traceId;
    private final Instant processedAt;
    private final String externalReference;
    private final boolean success;
}