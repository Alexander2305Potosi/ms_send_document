package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Represents a document to be processed.
 * Contains content (Base64 encoded in storage), metadata, and processing status.
 */
@Getter
@Builder
public class ProductDocumentToProcess {
    private final String documentId;
    private final String productId;
    private final String parentDocumentId;
    private final String filename;
    private final byte[] content;
    private final String contentType;
    private final String origin;
    private final String status;
    private final Instant createdAt;
    private final Instant processedAt;
    private final String correlationId;
    private final String errorCode;
    @Builder.Default
    private final boolean isZipArchive = false;
    @Builder.Default
    private final double fileSizeMb = 0.0;
    @Builder.Default
    private final String parentFolder = ".";
    @Builder.Default
    private final String childFolder = ".";
    @Builder.Default
    private final long fileSizeBytes = 0;
    @Builder.Default
    private final boolean skipped = false;

    public ProductDocumentToProcess withContent(byte[] newContent, String newFilename, String newContentType, double newFileSizeMb) {
        return ProductDocumentToProcess.builder()
            .documentId(this.documentId)
            .productId(this.productId)
            .parentDocumentId(this.parentDocumentId)
            .filename(newFilename != null ? newFilename : this.filename)
            .content(newContent)
            .contentType(newContentType != null ? newContentType : this.contentType)
            .origin(this.origin)
            .status(this.status)
            .createdAt(this.createdAt)
            .processedAt(this.processedAt)
            .correlationId(this.correlationId)
            .errorCode(this.errorCode)
            .isZipArchive(this.isZipArchive)
            .fileSizeMb(newFileSizeMb)
            .parentFolder(this.parentFolder)
            .childFolder(this.childFolder)
            .fileSizeBytes((long) (newFileSizeMb * 1024 * 1024))
            .skipped(this.skipped)
            .build();
    }

    public ProductDocumentToProcess withSkipped(boolean skipped) {
        return ProductDocumentToProcess.builder()
            .documentId(this.documentId)
            .productId(this.productId)
            .parentDocumentId(this.parentDocumentId)
            .filename(this.filename)
            .content(this.content)
            .contentType(this.contentType)
            .origin(this.origin)
            .status(this.status)
            .createdAt(this.createdAt)
            .processedAt(this.processedAt)
            .correlationId(this.correlationId)
            .errorCode(this.errorCode)
            .isZipArchive(this.isZipArchive)
            .fileSizeMb(this.fileSizeMb)
            .parentFolder(this.parentFolder)
            .childFolder(this.childFolder)
            .fileSizeBytes(this.fileSizeBytes)
            .skipped(skipped)
            .build();
    }
}
