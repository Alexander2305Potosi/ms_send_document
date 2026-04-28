package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;

import java.util.List;

/**
 * Encapsulates document validation rules for the processing pipeline.
 *
 * This record holds configuration for validating documents before they are sent
 * to external services (SOAP, S3, etc.). It provides methods to check:
 * - Folder-based skipping rules
 * - Origin pattern matching
 * - File size limits
 * - Folder information extraction for routing
 *
 * @see FileValidationConfig
 */
public record DocumentValidationRules(FileValidationConfig config) {

    /** Conversion factor: bytes per megabyte */
    public static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    /**
     * Determines if a document should be skipped based on its folder path.
     * Documents originating from folders in the skip list are marked as SKIPPED.
     *
     * @param origin the document's origin path (e.g., "/tmp/uploads/file.pdf")
     * @return true if the origin contains any folder to skip, false otherwise
     */
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

    /**
     * Determines if a document should be sent based on its origin pattern.
     * Documents whose origin matches at least one pattern are eligible for sending.
     *
     * @param origin the document's origin path
     * @return true if the origin matches at least one pattern, false if no patterns configured or origin is null/blank
     */
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

    /**
     * Determines if a document should NOT be sent based on its file size.
     * Documents exceeding the configured maximum size are marked as NOT_SENT.
     *
     * @param sizeBytes the file size in bytes
     * @return true if the file exceeds the maximum allowed size, false otherwise
     */
    public boolean shouldNotSendBySize(long sizeBytes) {
        int maxSizeMb = config.maxFileSizeMb();
        if (maxSizeMb <= 0) {
            return false;
        }
        return sizeBytes >= (long) maxSizeMb * BYTES_PER_MEGABYTE;
    }

    /**
     * Extracts folder routing information from the document's origin path.
     * Uses configured keywords to identify parent and child folders for routing.
     *
     * @param origin the document's origin path
     * @return FolderInfo containing parentFolder and childFolder for routing
     */
    public FolderInfo extractFolderInfo(String origin) {
        List<String> keywords = config.keywords();
        if (keywords == null || keywords.isEmpty() || origin == null || origin.isBlank()) {
            return new FolderInfo(ApiConstants.DEFAULT_FOLDER, ApiConstants.DEFAULT_FOLDER);
        }

        for (String keyword : keywords) {
            if (origin.contains(keyword)) {
                String[] parts = origin.split("/");
                if (parts.length >= 2) {
                    String childFolder = parts[parts.length - 1];
                    String parentFolder = parts.length > 1 ? parts[parts.length - 2] : ApiConstants.DEFAULT_FOLDER;
                    return new FolderInfo(parentFolder, childFolder);
                }
                return new FolderInfo(origin, ApiConstants.DEFAULT_FOLDER);
            }
        }
        return new FolderInfo(ApiConstants.DEFAULT_FOLDER, ApiConstants.DEFAULT_FOLDER);
    }

    /**
     * Represents folder routing information for document processing.
     *
     * @param parentFolder the parent folder for routing (e.g., "incoming")
     * @param childFolder the child folder for routing (e.g., "documents")
     */
    public record FolderInfo(String parentFolder, String childFolder) {}
}
