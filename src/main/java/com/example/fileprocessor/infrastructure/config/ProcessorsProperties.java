package com.example.fileprocessor.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.processors")
public record ProcessorsProperties(
    ProcessorConfig s3,
    ProcessorConfig soap,
    String zipTempDir
) {
    public record ProcessorConfig(
        Long maxFileSizeBytes,
        String filenamePattern
    ) {}
}
