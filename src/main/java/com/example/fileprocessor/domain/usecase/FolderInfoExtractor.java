package com.example.fileprocessor.domain.usecase;

import java.util.List;

/**
 * Extracts folder information from a document's origin path using keyword matching.
 */
public class FolderInfoExtractor {

    private final List<String> keywords;

    public FolderInfoExtractor(List<String> keywords) {
        this.keywords = keywords != null ? keywords : List.of();
    }

    public FolderInfo extract(String origin) {
        if (keywords.isEmpty() || origin == null || origin.isBlank()) {
            return FolderInfo.root();
        }

        for (String keyword : keywords) {
            if (origin.contains(keyword)) {
                String[] parts = origin.split("/");
                if (parts.length >= 2) {
                    return new FolderInfo(parts[parts.length - 2], parts[parts.length - 1]);
                }
                return new FolderInfo(origin, ".");
            }
        }
        return FolderInfo.root();
    }
}
