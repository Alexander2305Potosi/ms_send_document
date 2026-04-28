package com.example.fileprocessor.domain.port.in;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration for file validation.
 * This interface is implemented by infrastructure to provide
 * configuration values while maintaining domain independence.
 */
public interface FileValidationConfig {
    long maxSize();
    String allowedTypes();

    default List<String> keywords() {
        return List.of();
    }

    // ============ VALIDATION FLAGS ============

    default boolean shouldValidateSize() {
        return maxSize() > 0;
    }

    default boolean shouldValidateExtension() {
        return allowedTypes() != null && !allowedTypes().isBlank();
    }
}