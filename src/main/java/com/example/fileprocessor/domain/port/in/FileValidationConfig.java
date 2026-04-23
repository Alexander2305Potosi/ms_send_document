package com.example.fileprocessor.domain.port.in;

/**
 * Configuration for file validation.
 * This interface is implemented by infrastructure to provide
 * configuration values while maintaining domain independence.
 */
public interface FileValidationConfig {
    long maxSize();
    String allowedTypes();
    int maxFilenameLength();
}
