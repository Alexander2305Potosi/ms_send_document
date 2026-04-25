package com.example.fileprocessor.infrastructure.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.rest")
public record DocumentRestProperties(
    String endpoint,
    String listPath,
    String getPath,
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
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}