package com.example.fileprocessor.infrastructure.helpers.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.processors.s3")
public class S3ProcessorProperties implements FileValidationConfig {

    @Min(1024)
    private long maxSize = 52428800L; // 50MB default

    @NotBlank
    private String allowedTypes = "pdf,txt,csv,zip";

    @Min(10)
    private int maxFilenameLength = 255;

    private List<String> foldersToSkip = List.of("/tmp");

    @Min(1)
    private int maxFileSizeMb = 100; // S3 can handle larger files

    private List<String> keywords = List.of("test", "mock", "archive");

    private List<String> originPatternsToSend = List.of("incoming", "documents", "archive");

    @Override
    public long maxSize() { return maxSize; }

    @Override
    public String allowedTypes() { return allowedTypes; }

    @Override
    public int maxFilenameLength() { return maxFilenameLength; }

    @Override
    public List<String> foldersToSkip() { return foldersToSkip; }

    @Override
    public int maxFileSizeMb() { return maxFileSizeMb; }

    @Override
    public List<String> keywords() { return keywords; }

    @Override
    public List<String> originPatternsToSend() { return originPatternsToSend; }
}