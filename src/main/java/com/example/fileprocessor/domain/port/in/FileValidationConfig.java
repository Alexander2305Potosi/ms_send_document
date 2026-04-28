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

    /**
     * Content type patterns that this processor accepts.
     * Used for gateway-specific content validation (e.g., SOAP accepts xml/text/pdf).
     * Default empty means accept all content types.
     */
    default List<String> contentTypePatterns() {
        return List.of();
    }

    /**
     * Set of allowed content type patterns as individual tokens.
     * E.g., "xml,txt,pdf" -> ["xml", "txt", "pdf"]
     */
    default Set<String> allowedContentTypeTokens() {
        return contentTypePatterns().stream()
            .flatMap(pattern -> java.util.Arrays.stream(pattern.split("[,\\s]+")))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());
    }

    // ============ VALIDATION FLAGS ============

    default boolean shouldValidateContentType() {
        return !contentTypePatterns().isEmpty();
    }

    default boolean shouldValidateSize() {
        return maxSize() > 0;
    }

    default boolean shouldValidateExtension() {
        return allowedTypes() != null && !allowedTypes().isBlank();
    }

    default boolean shouldValidateFilename() {
        return maxFilenameLength() > 0;
    }
}