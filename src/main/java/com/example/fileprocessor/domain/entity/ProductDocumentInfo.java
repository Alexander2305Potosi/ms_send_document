package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.Builder;

@Builder
public record ProductDocumentInfo(
    String documentId,
    String filename,
    byte[] content,
    String contentType,
    long size,
    boolean isZip,
    String origin
) {
    public String extension() {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    public boolean isZipArchive() {
        return isZip || ApiConstants.EXTENSION_ZIP.equalsIgnoreCase(extension());
    }
}
