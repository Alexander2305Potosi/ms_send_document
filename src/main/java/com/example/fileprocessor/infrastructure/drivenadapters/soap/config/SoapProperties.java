package com.example.fileprocessor.infrastructure.drivenadapters.soap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.soap")
public record SoapProperties(
    @NotBlank
    String endpoint,

    @Min(1)
    int timeoutSeconds,

    @Min(0)
    int retryAttempts,

    @Min(100)
    int retryBackoffMillis
) {
}