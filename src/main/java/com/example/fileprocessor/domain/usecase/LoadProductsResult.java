package com.example.fileprocessor.domain.usecase;

import java.time.Instant;

public class LoadProductsResult {
    private final String productId;
    private final String name;
    private final int documentCount;
    private final String status;
    private final String message;
    private final String traceId;
    private final Instant processedAt;
    private final boolean success;

    private LoadProductsResult(Builder builder) {
        this.productId = builder.productId;
        this.name = builder.name;
        this.documentCount = builder.documentCount;
        this.status = builder.status;
        this.message = builder.message;
        this.traceId = builder.traceId;
        this.processedAt = builder.processedAt;
        this.success = builder.success;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String productId;
        private String name;
        private int documentCount;
        private String status;
        private String message;
        private String traceId;
        private Instant processedAt;
        private boolean success;

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder documentCount(int documentCount) {
            this.documentCount = documentCount;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder processedAt(Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public LoadProductsResult build() {
            return new LoadProductsResult(this);
        }
    }
}
