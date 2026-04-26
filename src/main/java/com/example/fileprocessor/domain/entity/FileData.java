package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Holds file content and metadata for document processing.
 * Used to transfer file data through the processing pipeline.
 */
@Getter
@Builder
public class FileData {
    private final String documentId;
    private final byte[] content;
    private final String filename;
    private final long size;
    private final String contentType;
    private final String traceId;

    public String extension() {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }
}