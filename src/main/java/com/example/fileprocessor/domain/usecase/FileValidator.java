package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Unified file and document validation.
 * Only three validations: format, size, and folder exclusion (handled separately).
 */
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private final FileValidationConfig config;

    public FileValidationConfig getConfig() {
        return config;
    }

    public FileValidator(FileValidationConfig config) {
        this.config = config;
    }

    /**
     * Runs enabled validations:
     * 1. Extension/Format validation
     * 2. File size validation
     */
    public Mono<ProductDocumentToProcess> validate(ProductDocumentToProcess document) {
        Set<String> allowedExtensions = parseAllowedExtensions(config.allowedTypes());

        return Mono.just(document)
            .flatMap(doc -> validateExtension(doc, allowedExtensions))
            .flatMap(doc -> validateSize(doc, config.maxSize()));
    }

    // ============ EXTENSION VALIDATION ============

    private Mono<ProductDocumentToProcess> validateExtension(ProductDocumentToProcess document, Set<String> allowedExtensions) {
        String ext = extractFileExtension(document);
        if (!allowedExtensions.contains(ext.toLowerCase())) {
            log.warn("Document {} rejected: extension '{}' not in allowed list {}",
                document.getFilename(), ext, allowedExtensions);
            return Mono.error(new FileValidationException(
                "File type '" + ext + "' not allowed. Allowed: " + config.allowedTypes(),
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }
        return Mono.just(document);
    }

    // ============ FILE SIZE VALIDATION ============

    private Mono<ProductDocumentToProcess> validateSize(ProductDocumentToProcess document, long maxSize) {
        long size = extractFileSize(document);

        if (size > maxSize) {
            log.warn("Document {} exceeds max size: {} > {} bytes",
                document.getFilename(), size, maxSize);
            return Mono.error(new FileValidationException(
                "File size " + size + " exceeds limit of " + maxSize + " bytes",
                ProcessingResultCodes.FILE_SIZE_EXCEEDED));
        }
        return Mono.just(document);
    }

    // ============ FOLDER INFO EXTRACTION ============

    public FolderInfo extractFolderInfo(String origin) {
        var keywords = config.keywords();
        if (keywords == null || keywords.isEmpty() || origin == null || origin.isBlank()) {
            return new FolderInfo(".", ".");
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
        return new FolderInfo(".", ".");
    }

    public record FolderInfo(String parentFolder, String childFolder) {}

    // ============ PRIVATE HELPERS ============

    private long extractFileSize(ProductDocumentToProcess p) {
        return p.getContent() != null ? p.getContent().length : 0;
    }

    private String extractFileExtension(ProductDocumentToProcess p) {
        String filename = p.getFilename();
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private Set<String> parseAllowedExtensions(String allowedTypes) {
        if (allowedTypes == null || allowedTypes.isBlank()) {
            return Set.of();
        }
        return Set.of(allowedTypes.split(",")).stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(ext -> !ext.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
