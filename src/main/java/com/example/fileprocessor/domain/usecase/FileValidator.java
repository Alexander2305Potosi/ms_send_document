package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates file extension and size against configured limits.
 */
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private final double maxSizeMb;
    private final String allowedTypes;
    private final Set<String> allowedExtensions;

    public FileValidator(double maxSizeMb, String allowedTypes) {
        this.maxSizeMb = maxSizeMb;
        this.allowedTypes = allowedTypes;
        this.allowedExtensions = parseExtensions(allowedTypes);
    }

    double getMaxSize() { return maxSizeMb; }
    String getAllowedTypes() { return allowedTypes; }

    public Mono<ProductDocumentToProcess> validate(ProductDocumentToProcess document) {
        return Mono.just(document)
            .flatMap(this::validateExtension)
            .flatMap(this::validateSize);
    }

    private Mono<ProductDocumentToProcess> validateExtension(ProductDocumentToProcess document) {
        String ext = extractExtension(document.getFilename());
        if (!allowedExtensions.contains(ext.toLowerCase())) {
            log.warn("Document {} rejected: extension '{}' not in allowed list {}",
                document.getFilename(), ext, allowedExtensions);
            return Mono.error(new FileValidationException(
                "File type '" + ext + "' not allowed",
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }
        return Mono.just(document);
    }

    private Mono<ProductDocumentToProcess> validateSize(ProductDocumentToProcess document) {
        double sizeMb = document.getFileSizeMb();
        if (sizeMb > maxSizeMb) {
            log.warn("Document {} exceeds max size: {} > {} MB",
                document.getFilename(), sizeMb, maxSizeMb);
            return Mono.error(new FileValidationException(
                "File size " + sizeMb + " MB exceeds limit of " + maxSizeMb + " MB",
                ProcessingResultCodes.FILE_SIZE_EXCEEDED));
        }
        return Mono.just(document);
    }

    private static String extractExtension(String filename) {
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private static Set<String> parseExtensions(String allowedTypes) {
        if (allowedTypes == null || allowedTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedTypes.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(ext -> !ext.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
