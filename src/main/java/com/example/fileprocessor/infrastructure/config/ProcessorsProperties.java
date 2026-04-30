package com.example.fileprocessor.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.processors")
public record ProcessorsProperties(
    @NotNull S3Validation s3,
    @NotNull SoapValidation soap
) {
    public record S3Validation(
        @Min(0) long maxFileSizeBytes,
        @NotBlank String filenamePattern
    ) {}

    public record SoapValidation(
        @Min(0) long maxFileSizeBytes,
        @NotBlank String filenamePattern
    ) {}
}
