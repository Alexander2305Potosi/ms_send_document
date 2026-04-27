package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentConstants;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;

import java.util.List;

/**
 * Encapsulates document validation rules.
 * Used by AbstractProcessDocumentsUseCase to validate documents before sending.
 */
public record DocumentValidationRules(FileValidationConfig config) {

    public static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    public boolean shouldSkipFolder(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        List<String> foldersToSkip = config.foldersToSkip();
        if (foldersToSkip == null || foldersToSkip.isEmpty()) {
            return false;
        }
        return foldersToSkip.stream().anyMatch(folder -> origin.contains(folder));
    }

    public boolean shouldSendByOrigin(String origin) {
        List<String> patterns = config.originPatternsToSend();
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> origin.contains(pattern));
    }

    public boolean shouldNotSendBySize(long sizeBytes) {
        int maxSizeMb = config.maxFileSizeMb();
        if (maxSizeMb <= 0) {
            return false;
        }
        return sizeBytes >= (long) maxSizeMb * BYTES_PER_MEGABYTE;
    }

    public FolderInfo extractFolderInfo(String origin) {
        List<String> keywords = config.keywords();
        if (keywords == null || keywords.isEmpty() || origin == null || origin.isBlank()) {
            return new FolderInfo(ProductDocumentConstants.DEFAULT_FOLDER, ProductDocumentConstants.DEFAULT_FOLDER);
        }

        for (String keyword : keywords) {
            if (origin.contains(keyword)) {
                String[] parts = origin.split("/");
                if (parts.length >= 2) {
                    String childFolder = parts[parts.length - 1];
                    String parentFolder = parts.length > 1 ? parts[parts.length - 2] : ProductDocumentConstants.DEFAULT_FOLDER;
                    return new FolderInfo(parentFolder, childFolder);
                }
                return new FolderInfo(origin, ProductDocumentConstants.DEFAULT_FOLDER);
            }
        }
        return new FolderInfo(ProductDocumentConstants.DEFAULT_FOLDER, ProductDocumentConstants.DEFAULT_FOLDER);
    }

    public record FolderInfo(String parentFolder, String childFolder) {}
}
