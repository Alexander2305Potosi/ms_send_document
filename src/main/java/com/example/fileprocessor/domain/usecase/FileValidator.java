package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates file data before processing.
 * Ensures files meet size, type, and naming requirements.
 */
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);

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
     * Validates a product document against all rules.
     * @param pending the document to validate
     * @return Mono with validated ProductDocumentToProcess or error
     */
    public Mono<ProductDocumentToProcess> validate(ProductDocumentToProcess pending) {
        return Mono.just(pending)
            .flatMap(this::validateSize)
            .flatMap(this::validateExtension)
            .flatMap(this::validateFilename);
    }

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

        if (!allowedTypes.contains(ext.toLowerCase())) {
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
        if (filename.contains(ApiConstants.PATH_DOUBLE_DOT) || filename.contains(ApiConstants.PATH_SLASH) || filename.contains(ApiConstants.PATH_BACKSLASH)) {
            log.warn("Filename contains invalid characters: {}", filename);
            return Mono.error(new FileValidationException(
                ProcessingMessages.MSG_FILENAME_INVALID,
                ProcessingResultCodes.INVALID_FILENAME));
        }
        return Mono.just(p);
    }
}
