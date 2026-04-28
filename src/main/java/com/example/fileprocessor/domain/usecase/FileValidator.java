package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unified file and document validation.
 * Combines file content validation with routing/folder decisions.
 */
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

    private static final String PATH_DOUBLE_DOT = "..";
    private static final String PATH_SLASH = "/";
    private static final String PATH_BACKSLASH = "\\";
    private static final String DEFAULT_FOLDER = ".";
    public static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final FileValidationConfig config;
    private final Set<String> allowedTypes;

    public FileValidator(FileValidationConfig config) {
        this.config = config;
        this.allowedTypes = Arrays.stream(config.allowedTypes().split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Validates file content (size, extension, filename).
     */
    public Mono<ProductDocumentToProcess> validate(ProductDocumentToProcess pending) {
        return Mono.just(pending)
            .flatMap(this::validateSize)
            .flatMap(this::validateExtension)
            .flatMap(this::validateFilename);
    }

    // ============ ROUTING METHODS ============

    /**
     * Determines if a document should be skipped based on its folder path.
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
     */
    public FolderInfo extractFolderInfo(String origin) {
        List<String> keywords = config.keywords();
        if (keywords == null || keywords.isEmpty() || origin == null || origin.isBlank()) {
            return new FolderInfo(DEFAULT_FOLDER, DEFAULT_FOLDER);
        }

        for (String keyword : keywords) {
            if (origin.contains(keyword)) {
                String[] parts = origin.split("/");
                if (parts.length >= 2) {
                    String childFolder = parts[parts.length - 1];
                    String parentFolder = parts.length > 1 ? parts[parts.length - 2] : DEFAULT_FOLDER;
                    return new FolderInfo(parentFolder, childFolder);
                }
                return new FolderInfo(origin, DEFAULT_FOLDER);
            }
        }
        return new FolderInfo(DEFAULT_FOLDER, DEFAULT_FOLDER);
    }

    /**
     * Routing information for document processing.
     */
    public record FolderInfo(String parentFolder, String childFolder) {}

    // ============ PRIVATE FILE VALIDATION ============

    private long fileSize(ProductDocumentToProcess p) {
        return p.getContent() != null ? p.getContent().length : 0;
    }

    private String extension(ProductDocumentToProcess p) {
        String filename = p.getFilename();
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private Mono<ProductDocumentToProcess> validateSize(ProductDocumentToProcess p) {
        long size = fileSize(p);
        if (size > config.maxSize()) {
            log.warn("File {} exceeds max size: {} > {}",
                p.getFilename(), size, config.maxSize());
            return Mono.error(new FileValidationException(
                ProcessingMessages.MSG_FILE_SIZE_EXCEEDED + config.maxSize() + " bytes",
                ProcessingResultCodes.FILE_SIZE_EXCEEDED));
        }
        return Mono.just(p);
    }

    private Mono<ProductDocumentToProcess> validateExtension(ProductDocumentToProcess p) {
        String ext = extension(p);
        if (!allowedTypes.contains(ext)) {
            log.warn("File {} has invalid extension: {}",
                p.getFilename(), ext);
            return Mono.error(new FileValidationException(
                ProcessingMessages.MSG_FILE_TYPE_NOT_ALLOWED + config.allowedTypes(),
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }
        return Mono.just(p);
    }

    private Mono<ProductDocumentToProcess> validateFilename(ProductDocumentToProcess p) {
        String filename = p.getFilename();
        if (filename.length() > config.maxFilenameLength()) {
            log.warn("Filename exceeds max length: {}", filename.length());
            return Mono.error(new FileValidationException(
                ProcessingMessages.MSG_FILENAME_TOO_LONG + config.maxFilenameLength(),
                ProcessingResultCodes.FILENAME_TOO_LONG));
        }
        if (filename.contains(PATH_DOUBLE_DOT) || filename.contains(PATH_SLASH) || filename.contains(PATH_BACKSLASH)) {
            log.warn("Filename contains invalid characters: {}", filename);
            return Mono.error(new FileValidationException(
                ProcessingMessages.MSG_FILENAME_INVALID,
                ProcessingResultCodes.INVALID_FILENAME));
        }
        return Mono.just(p);
    }
}