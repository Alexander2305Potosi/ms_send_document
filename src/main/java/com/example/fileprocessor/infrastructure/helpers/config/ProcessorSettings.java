package com.example.fileprocessor.infrastructure.helpers.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Processor-specific settings bound via application.yml under app.processors.{soap|s3}.
 */
public class ProcessorSettings {

    @Min(1)
    private double maxSize;

    @NotBlank
    private String allowedTypes;

    private List<String> keywords = List.of();

    private List<String> folderExclusionRegex = List.of();

    public double getMaxSize() { return maxSize; }
    public void setMaxSize(double maxSize) { this.maxSize = maxSize; }

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
}
