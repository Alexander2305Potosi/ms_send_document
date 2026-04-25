package com.example.fileprocessor.domain.entity;

import java.time.Instant;

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
    private final String traceId;
    private final String soapCorrelationId;
    private final String errorCode;

    private ProductDocumentToProcess(Builder builder) {
        this.documentId = builder.documentId;
        this.productId = builder.productId;
        this.parentDocumentId = builder.parentDocumentId;
        this.filename = builder.filename;
        this.content = builder.content;
        this.contentType = builder.contentType;
        this.origin = builder.origin;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.processedAt = builder.processedAt;
        this.traceId = builder.traceId;
        this.soapCorrelationId = builder.soapCorrelationId;
        this.errorCode = builder.errorCode;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getProductId() {
        return productId;
    }

    public String getParentDocumentId() {
        return parentDocumentId;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public String getOrigin() {
        return origin;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSoapCorrelationId() {
        return soapCorrelationId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String documentId;
        private String productId;
        private String parentDocumentId;
        private String filename;
        private byte[] content;
        private String contentType;
        private String origin;
        private String status;
        private Instant createdAt;
        private Instant processedAt;
        private String traceId;
        private String soapCorrelationId;
        private String errorCode;

        public Builder documentId(String documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder parentDocumentId(String parentDocumentId) {
            this.parentDocumentId = parentDocumentId;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder content(byte[] content) {
            this.content = content;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder processedAt(Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder soapCorrelationId(String soapCorrelationId) {
            this.soapCorrelationId = soapCorrelationId;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public ProductDocumentToProcess build() {
            return new ProductDocumentToProcess(this);
        }
    }
}
