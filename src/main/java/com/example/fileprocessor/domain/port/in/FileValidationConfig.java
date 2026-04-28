package com.example.fileprocessor.domain.port.in;

import java.util.List;

/**
 * Configuration for file validation.
 * This interface is implemented by infrastructure to provide
 * configuration values while maintaining domain independence.
 */
public interface FileValidationConfig {
    long maxSize();
    String allowedTypes();
    int maxFilenameLength();
    int maxFileSizeMb();

    default List<String> foldersToSkip() {
        return List.of();
    }

    default List<String> keywords() {
        return List.of();
    }

    default List<String> originPatternsToSend() {
        return List.of();
    }
}