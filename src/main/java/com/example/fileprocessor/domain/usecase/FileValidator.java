package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
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
     * Validates a file against all rules.
     * @param fileData the file to validate
     * @return Mono with validated FileData or error
     */
    public Mono<FileData> validate(FileData fileData) {
        return Mono.just(fileData)
            .flatMap(this::validateSize)
            .flatMap(this::validateExtension)
            .flatMap(this::validateFilename);
    }

    private Mono<FileData> validateSize(FileData fileData) {
        if (fileData.getSize() > config.maxSize()) {
            log.warn("File {} exceeds max size: {} > {}",
                fileData.getFilename(), fileData.getSize(), config.maxSize());
            return Mono.error(new FileValidationException(
                FileValidationErrorCodes.MSG_FILE_SIZE_EXCEEDED + config.maxSize() + " bytes",
                FileValidationErrorCodes.FILE_SIZE_EXCEEDED));
        }
        return Mono.just(fileData);
    }

    private Mono<FileData> validateExtension(FileData fileData) {
        String extension = fileData.extension();

        if (!allowedTypes.contains(extension.toLowerCase())) {
            log.warn("File {} has invalid extension: {}",
                fileData.getFilename(), extension);
            return Mono.error(new FileValidationException(
                FileValidationErrorCodes.MSG_FILE_TYPE_NOT_ALLOWED + config.allowedTypes(),
                FileValidationErrorCodes.INVALID_FILE_TYPE));
        }
        return Mono.just(fileData);
    }

    private Mono<FileData> validateFilename(FileData fileData) {
        String filename = fileData.getFilename();
        if (filename.length() > config.maxFilenameLength()) {
            log.warn("Filename exceeds max length: {}", filename.length());
            return Mono.error(new FileValidationException(
                FileValidationErrorCodes.MSG_FILENAME_TOO_LONG + config.maxFilenameLength(),
                FileValidationErrorCodes.FILENAME_TOO_LONG));
        }
        if (filename.contains(FileValidatorConstants.PATH_DOUBLE_DOT) || filename.contains(FileValidatorConstants.PATH_SLASH) || filename.contains(FileValidatorConstants.PATH_BACKSLASH)) {
            log.warn("Filename contains invalid characters: {}", filename);
            return Mono.error(new FileValidationException(
                FileValidationErrorCodes.MSG_FILENAME_INVALID,
                FileValidationErrorCodes.INVALID_FILENAME));
        }
        return Mono.just(fileData);
    }
}