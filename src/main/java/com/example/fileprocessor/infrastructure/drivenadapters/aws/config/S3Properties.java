package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
    @NotBlank
    String bucketName,

    @NotBlank
    String region,

    String endpoint,

    boolean pathStyleAccess,

    String accessKey,

    String secretKey,

    int retryAttempts,

    int retryBackoffMillis,

    int timeoutSeconds,

    String keyPrefix
) {
    public S3Properties {
        if (retryAttempts <= 0) retryAttempts = 3;
        if (retryBackoffMillis < 100) retryBackoffMillis = 500;
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (keyPrefix == null || keyPrefix.isBlank()) keyPrefix = "documents/";
    }
}
