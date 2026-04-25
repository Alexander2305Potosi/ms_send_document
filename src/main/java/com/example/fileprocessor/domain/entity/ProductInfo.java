package com.example.fileprocessor.domain.entity;

import java.util.List;

public class ProductInfo {
    private final String productId;
    private final String name;
    private final List<ProductDocumentInfo> documents;

    private ProductInfo(Builder builder) {
        this.productId = builder.productId;
        this.name = builder.name;
        this.documents = builder.documents;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public List<ProductDocumentInfo> getDocuments() {
        return documents;
    }

    public String extension() {
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String productId;
        private String name;
        private List<ProductDocumentInfo> documents;

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder documents(List<ProductDocumentInfo> documents) {
            this.documents = documents;
            return this;
        }

        public ProductInfo build() {
            return new ProductInfo(this);
        }
    }
}
