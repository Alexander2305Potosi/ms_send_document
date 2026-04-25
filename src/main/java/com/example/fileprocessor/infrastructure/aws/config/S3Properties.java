package com.example.fileprocessor.infrastructure.aws.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for AWS S3 operations.
 */
@Validated
@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
    @NotBlank
    String bucketName,

    @NotBlank
    String region,

    String endpoint,

    boolean pathStyleAccess
) {}