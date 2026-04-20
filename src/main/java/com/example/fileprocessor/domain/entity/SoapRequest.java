package com.example.fileprocessor.domain.entity;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

public record SoapRequest(
    String fileContentBase64,
    String filename,
    String contentType,
    long fileSize,
    String traceId,
    Instant timestamp
) {
    public SoapRequest {
        Objects.requireNonNull(fileContentBase64, "fileContentBase64 must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static SoapRequest fromFileData(FileData fileData) {
        return new SoapRequest(
            Base64.getEncoder().encodeToString(fileData.content()),
            fileData.filename(),
            fileData.contentType(),
            fileData.size(),
            fileData.traceId(),
            Instant.now()
        );
    }
}
