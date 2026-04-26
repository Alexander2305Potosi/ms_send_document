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

    boolean pathStyleAccess
) {
}