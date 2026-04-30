package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.domain.service.DocumentValidator.ValidationConfig;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.processors")
public record ProcessorsProperties(
    S3Config s3,
    SoapConfig soap
) {
    public record S3Config(
        Long maxFileSizeBytes,
        String filenamePattern
    ) {}

    public record SoapConfig(
        Long maxFileSizeBytes,
        String filenamePattern
    ) {}

    public ValidationConfig toValidationConfig(S3Config c) {
        return new ValidationConfig(c.maxFileSizeBytes(), c.filenamePattern());
    }

    public ValidationConfig toValidationConfig(SoapConfig c) {
        return new ValidationConfig(c.maxFileSizeBytes(), c.filenamePattern());
    }
}
