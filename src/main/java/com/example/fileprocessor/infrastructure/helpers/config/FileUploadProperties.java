package com.example.fileprocessor.infrastructure.helpers.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.file")
public record FileUploadProperties(
    @Min(1024)
    long maxSize,

    @NotBlank
    String allowedTypes,

    @Min(10)
    int maxFilenameLength,

    List<String> foldersToSkip,

    @Min(1)
    int maxFileSizeMb,

    List<String> keywords,

    List<String> originPatternsToSend
) implements FileValidationConfig {
}