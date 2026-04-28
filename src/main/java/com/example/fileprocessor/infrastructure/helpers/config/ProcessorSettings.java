package com.example.fileprocessor.infrastructure.helpers.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Processor-specific settings implementing FileValidationConfig.
 * Bound via application.yml under app.processors.{soap|s3}.
 */
public class ProcessorSettings implements FileValidationConfig {

    @Min(1024)
    private long maxSize;

    @NotBlank
    private String allowedTypes;

    private List<String> keywords = List.of();

    private List<String> folderExclusionRegex = List.of();

    // ============ Getters/Setters for Spring property binding ============

    public long getMaxSize() { return maxSize; }
    public void setMaxSize(long maxSize) { this.maxSize = maxSize; }

    public String getAllowedTypes() { return allowedTypes; }
    public void setAllowedTypes(String allowedTypes) { this.allowedTypes = allowedTypes; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords != null ? keywords : List.of();
    }

    public List<String> getFolderExclusionRegex() { return folderExclusionRegex; }
    public void setFolderExclusionRegex(List<String> folderExclusionRegex) {
        this.folderExclusionRegex = folderExclusionRegex != null ? folderExclusionRegex : List.of();
    }

    // ============ FileValidationConfig ============

    @Override public long maxSize() { return maxSize; }
    @Override public String allowedTypes() { return allowedTypes; }
    @Override public List<String> keywords() { return keywords; }
}