package com.example.fileprocessor.infrastructure.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.rest")
public record DocumentRestProperties(
    String endpoint,
    String listPath,
    String getPath,
    String productsPath,
    String productDocumentsPath,
    int timeoutSeconds
) {
    public DocumentRestProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://localhost:3001";
        }
        if (listPath == null || listPath.isBlank()) {
            listPath = "/api/documents";
        }
        if (getPath == null || getPath.isBlank()) {
            getPath = "/api/document";
        }
        if (productsPath == null || productsPath.isBlank()) {
            productsPath = "/api/products";
        }
        if (productDocumentsPath == null || productDocumentsPath.isBlank()) {
            productDocumentsPath = "/api/products/{productId}/documents";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}