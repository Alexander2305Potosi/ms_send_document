package com.example.fileprocessor.domain.usecase;

import lombok.Getter;

/**
 * Result of loading a document from the external REST API.
 */
@Getter
public class LoadDocumentsResult {
    private final String documentId;
    private final String filename;
    private final String status;
    private final String message;
    private final String traceId;
    private final java.time.Instant processedAt;
    private final boolean success;

    public LoadDocumentsResult(String documentId, String filename, String status, String message,
                              String traceId, java.time.Instant processedAt, boolean success) {
        this.documentId = documentId;
        this.filename = filename;
        this.status = status;
        this.message = message;
        this.traceId = traceId;
        this.processedAt = processedAt;
        this.success = success;
    }

    public static LoadDocumentsResultBuilder builder() {
        return new LoadDocumentsResultBuilder();
    }

    public static class LoadDocumentsResultBuilder {
        private String documentId;
        private String filename;
        private String status;
        private String message;
        private String traceId;
        private java.time.Instant processedAt;
        private boolean success;

        public LoadDocumentsResultBuilder documentId(String documentId) {
            this.documentId = documentId;
            return this;
        }

        public LoadDocumentsResultBuilder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public LoadDocumentsResultBuilder status(String status) {
            this.status = status;
            return this;
        }

        public LoadDocumentsResultBuilder message(String message) {
            this.message = message;
            return this;
        }

        public LoadDocumentsResultBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public LoadDocumentsResultBuilder processedAt(java.time.Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public LoadDocumentsResultBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public LoadDocumentsResult build() {
            return new LoadDocumentsResult(documentId, filename, status, message, traceId, processedAt, success);
        }
    }
}
