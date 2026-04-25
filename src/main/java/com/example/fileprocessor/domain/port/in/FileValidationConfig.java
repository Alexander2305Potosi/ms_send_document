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
    List<String> foldersToSkip();
    int maxFileSizeMb();
    List<String> keywords();
    List<String> originPatternsToSend();
}
