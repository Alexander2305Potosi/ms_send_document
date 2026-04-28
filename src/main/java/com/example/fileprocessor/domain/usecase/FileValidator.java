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
 * Uses FileValidationConfig values for all thresholds and rules.
 * Each validation is conditional - only runs if config enables it.
 */
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private static final String PATH_DOUBLE_DOT = "..";
    private static final String PATH_SLASH = "/";
    private static final String PATH_BACKSLASH = "\\";
    private static final String DEFAULT_FOLDER = ".";

    private final FileValidationConfig config;
    private final Set<String> allowedExtensions;

    public FileValidator(FileValidationConfig config) {
        this.config = config;
        this.allowedExtensions = parseAllowedExtensions(config.allowedTypes());
    }

    /**
     * Runs all enabled validations in sequence:
     * 1. Content type (if shouldValidateContentType)
     * 2. File size (if shouldValidateSize)
     * 3. Extension (if shouldValidateExtension)
     * 4. Filename (if shouldValidateFilename)
     */
    public Mono<ProductDocumentToProcess> validate(ProductDocumentToProcess document) {
        return Mono.just(document)
            .flatMap(doc -> maybeValidate(doc, config.shouldValidateContentType(), this::validateContentType))
            .flatMap(doc -> maybeValidate(doc, config.shouldValidateSize(), this::validateSize))
            .flatMap(doc -> maybeValidate(doc, config.shouldValidateExtension(), this::validateExtension))
            .flatMap(doc -> maybeValidate(doc, config.shouldValidateFilename(), this::validateFilename));
    }

    private Mono<ProductDocumentToProcess> maybeValidate(
            ProductDocumentToProcess doc,
            boolean shouldValidate,
            java.util.function.Function<ProductDocumentToProcess, Mono<ProductDocumentToProcess>> validator) {
        return shouldValidate ? validator.apply(doc) : Mono.just(doc);
    }

    // ============ CONTENT TYPE VALIDATION ============

    private Mono<ProductDocumentToProcess> validateContentType(ProductDocumentToProcess document) {
        Set<String> allowedTokens = config.allowedContentTypeTokens();
        if (allowedTokens.isEmpty()) {
            return Mono.just(document);
        }

        String contentType = document.getContentType();
        if (contentType == null) {
            log.warn("Document {} has no content type", document.getFilename());
            return Mono.error(new FileValidationException(
                "Missing content type",
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }

        boolean matches = allowedTokens.stream()
            .anyMatch(token -> contentType.contains(token));
        if (!matches) {
            log.warn("Document {} rejected: content type '{}' not in allowed list {}",
                document.getFilename(), contentType, allowedTokens);
            return Mono.error(new FileValidationException(
                "Unsupported content type: " + contentType,
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }

        return Mono.just(document);
    }

    // ============ FILE SIZE VALIDATION ============

    private Mono<ProductDocumentToProcess> validateSize(ProductDocumentToProcess document) {
        long size = fileSize(document);
        long maxSize = config.maxSize();

        if (size > maxSize) {
            log.warn("Document {} exceeds max size: {} > {} bytes",
                document.getFilename(), size, maxSize);
            return Mono.error(new FileValidationException(
                "File size " + size + " exceeds limit of " + maxSize + " bytes",
                ProcessingResultCodes.FILE_SIZE_EXCEEDED));
        }
        return Mono.just(document);
    }

    // ============ EXTENSION VALIDATION ============

    private Mono<ProductDocumentToProcess> validateExtension(ProductDocumentToProcess document) {
        String ext = extension(document);
        if (!allowedExtensions.contains(ext.toLowerCase())) {
            log.warn("Document {} rejected: extension '{}' not in allowed list {}",
                document.getFilename(), ext, allowedExtensions);
            return Mono.error(new FileValidationException(
                "File type '" + ext + "' not allowed. Allowed: " + config.allowedTypes(),
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }
        return Mono.just(document);
    }

    // ============ FILENAME VALIDATION ============

    private Mono<ProductDocumentToProcess> validateFilename(ProductDocumentToProcess document) {
        String filename = document.getFilename();
        int maxLen = config.maxFilenameLength();

        if (filename.length() > maxLen) {
            log.warn("Document {} rejected: filename length {} > max {}",
                document.getFilename(), filename.length(), maxLen);
            return Mono.error(new FileValidationException(
                "Filename length " + filename.length() + " exceeds max " + maxLen,
                ProcessingResultCodes.FILENAME_TOO_LONG));
        }

        if (containsPathTraversal(filename)) {
            log.warn("Document {} rejected: filename contains path traversal chars",
                document.getFilename());
            return Mono.error(new FileValidationException(
                "Filename contains invalid path characters",
                ProcessingResultCodes.INVALID_FILENAME));
        }

        return Mono.just(document);
    }

    // ============ ROUTING HELPERS ============

    public FolderInfo extractFolderInfo(String origin) {
        var keywords = config.keywords();
        if (keywords == null || keywords.isEmpty() || origin == null || origin.isBlank()) {
            return new FolderInfo(DEFAULT_FOLDER, DEFAULT_FOLDER);
        }

        for (String keyword : keywords) {
            if (origin.contains(keyword)) {
                String[] parts = origin.split("/");
                if (parts.length >= 2) {
                    return new FolderInfo(parts[parts.length - 2], parts[parts.length - 1]);
                }
                return new FolderInfo(origin, DEFAULT_FOLDER);
            }
        }
        return new FolderInfo(DEFAULT_FOLDER, DEFAULT_FOLDER);
    }

    public record FolderInfo(String parentFolder, String childFolder) {}

    // ============ PRIVATE HELPERS ============

    private long fileSize(ProductDocumentToProcess p) {
        return p.getContent() != null ? p.getContent().length : 0;
    }

    private String extension(ProductDocumentToProcess p) {
        String filename = p.getFilename();
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private boolean containsPathTraversal(String filename) {
        return filename.contains(PATH_DOUBLE_DOT)
            || filename.contains(PATH_SLASH)
            || filename.contains(PATH_BACKSLASH);
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