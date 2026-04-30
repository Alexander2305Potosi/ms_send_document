package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared file validation utilities.
 * Centralizes extension parsing and file validation logic used by FileValidator and ZipProcessor.
 */
public final class FileValidationUtils {

    private static final Logger log = LoggerFactory.getLogger(FileValidationUtils.class);

    private FileValidationUtils() {}

    public static String extractExtension(String filename) {
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    public static Set<String> parseAllowedExtensions(String allowedTypes) {
        if (allowedTypes == null || allowedTypes.isBlank()) {
            return Set.of();
        }
        return Set.of(allowedTypes.split(",")).stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(ext -> !ext.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    public static void validateFile(String filename, long size, FileValidationConfig config) {
        String ext = extractExtension(filename);
        Set<String> allowedExtensions = parseAllowedExtensions(config.allowedTypes());

        if (!allowedExtensions.contains(ext.toLowerCase())) {
            log.warn("File {} has disallowed extension: '{}'", filename, ext);
            throw new FileValidationException(
                "File type '" + ext + "' not allowed. Allowed: " + config.allowedTypes(),
                ProcessingResultCodes.INVALID_FILE_TYPE);
        }

        if (size > config.maxSize()) {
            log.warn("File {} exceeds max size: {} > {} bytes", filename, size, config.maxSize());
            throw new FileValidationException(
                "File size " + size + " exceeds limit of " + config.maxSize() + " bytes",
                ProcessingResultCodes.FILE_SIZE_EXCEEDED);
        }
    }
}
