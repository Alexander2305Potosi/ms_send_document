package com.example.fileprocessor.infrastructure.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.rest")
public record DocumentRestProperties(
    String endpoint,
    String documentsPath,
    String documentPath,
    int timeoutSeconds
) {
    public DocumentRestProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://localhost:8081";
        }
        if (documentsPath == null || documentsPath.isBlank()) {
            documentsPath = "/api/documents";
        }
        if (documentPath == null || documentPath.isBlank()) {
            documentPath = "/api/document";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}