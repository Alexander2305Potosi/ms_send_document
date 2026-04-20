package com.example.fileprocessor.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.file")
public record FileUploadProperties(
    @Min(1024)
    long maxSize,

    @NotBlank
    String allowedTypes,

    @Min(10)
    int maxFilenameLength
) {
}
