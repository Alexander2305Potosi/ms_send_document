package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.animal-rest")
public record AnimalRestProperties(
    @NotBlank String endpoint,
    @NotBlank String animalDirectoryPath,
    @NotBlank String directoryTreePath,
    @Min(1) int timeoutSeconds
) {}
