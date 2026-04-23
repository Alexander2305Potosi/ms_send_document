package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents document metadata retrieved from the external REST API.
 */
@Getter
@Builder
public class DocumentInfo {
    private final String documentId;
    private final String filename;
    private final byte[] content;
    private final String contentType;
    private final long size;
    private final boolean isZip;

    public String extension() {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    public boolean isZipArchive() {
        return isZip || "zip".equalsIgnoreCase(extension());
    }
}