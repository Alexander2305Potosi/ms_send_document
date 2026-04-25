package com.example.fileprocessor.domain.entity;

import java.time.Instant;

public class ProductToProcess {
    private final String productId;
    private final String name;
    private final String status;
    private final Instant createdAt;
    private final Instant processedAt;
    private final String traceId;

    private ProductToProcess(Builder builder) {
        this.productId = builder.productId;
        this.name = builder.name;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.processedAt = builder.processedAt;
        this.traceId = builder.traceId;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String productId;
        private String name;
        private String status;
        private Instant createdAt;
        private Instant processedAt;
        private String traceId;

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
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

        public ProductToProcess build() {
            return new ProductToProcess(this);
        }
    }
}
