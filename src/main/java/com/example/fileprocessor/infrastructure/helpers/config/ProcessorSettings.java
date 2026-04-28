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

    @Min(10)
    private int maxFilenameLength = 255;

    private List<String> foldersToSkip = List.of();

    @Min(1)
    private int maxFileSizeMb;

    private List<String> keywords = List.of();

    private List<String> originPatternsToSend = List.of();

    private List<String> folderExclusionRegex = List.of();

    @Min(1)
    private int maxConcurrency = 10;

    // ============ Getters/Setters for Spring property binding ============

    public long getMaxSize() { return maxSize; }
    public void setMaxSize(long maxSize) { this.maxSize = maxSize; }

    public String getAllowedTypes() { return allowedTypes; }
    public void setAllowedTypes(String allowedTypes) { this.allowedTypes = allowedTypes; }

    public int getMaxFilenameLength() { return maxFilenameLength; }
    public void setMaxFilenameLength(int maxFilenameLength) { this.maxFilenameLength = maxFilenameLength; }

    public List<String> getFoldersToSkip() { return foldersToSkip; }
    public void setFoldersToSkip(List<String> foldersToSkip) {
        this.foldersToSkip = foldersToSkip != null ? foldersToSkip : List.of();
    }

    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords != null ? keywords : List.of();
    }

    public List<String> getOriginPatternsToSend() { return originPatternsToSend; }
    public void setOriginPatternsToSend(List<String> originPatternsToSend) {
        this.originPatternsToSend = originPatternsToSend != null ? originPatternsToSend : List.of();
    }

    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency > 0 ? maxConcurrency : 10; }

    public List<String> getFolderExclusionRegex() { return folderExclusionRegex; }
    public void setFolderExclusionRegex(List<String> folderExclusionRegex) {
        this.folderExclusionRegex = folderExclusionRegex != null ? folderExclusionRegex : List.of();
    }

    // ============ FileValidationConfig ============

    @Override public long maxSize() { return maxSize; }
    @Override public String allowedTypes() { return allowedTypes; }
    @Override public int maxFilenameLength() { return maxFilenameLength; }
    @Override public List<String> foldersToSkip() { return foldersToSkip; }
    @Override public int maxFileSizeMb() { return maxFileSizeMb; }
    @Override public List<String> keywords() { return keywords; }
    @Override public List<String> originPatternsToSend() { return originPatternsToSend; }
}
