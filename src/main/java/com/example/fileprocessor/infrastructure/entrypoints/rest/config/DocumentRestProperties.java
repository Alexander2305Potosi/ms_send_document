package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.document-rest")
public record DocumentRestProperties(
    @NotBlank
    String endpoint,

    String listPath,

    String getPath,

    @NotBlank
    String productsPath,

    @NotBlank
    String productDocumentsPath,

    @Min(1)
    int timeoutSeconds
) {
}