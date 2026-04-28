package com.example.fileprocessor.domain.valueobject;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;

import java.time.Instant;

/**
 * Represents a checkpoint in the document processing flow.
 * Inmutable. Captures the exact state after a critical step.
 */
public record ProcessingCheckpoint(
    String documentId,
    String status,
    String correlationId,
    String errorCode,
    Instant timestamp
) {
    /**
     * Factory method to create a checkpoint from a document and upload result.
     */
    public static ProcessingCheckpoint from(ProductDocumentToProcess pending, FileUploadResult result) {
        return new ProcessingCheckpoint(
            pending.getDocumentId(),
            result.getStatus(),
            result.getCorrelationId(),
            result.getErrorCode(),
            Instant.now()
        );
    }
}
