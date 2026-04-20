package com.example.fileprocessor.domain.entity;

import java.util.Objects;

public record FileData(
    byte[] content,
    String filename,
    long size,
    String contentType,
    String traceId
) {
    public FileData {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }

    public String extension() {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }
}
