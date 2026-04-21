package com.example.fileprocessor.infrastructure.rest.dto;

public record FileUploadRequestDto(
    byte[] content,
    String filename,
    String contentType,
    long size,
    String traceId
) {
}
