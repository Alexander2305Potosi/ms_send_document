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
                "File size exceeds maximum allowed: " + config.maxSize() + " bytes",
                "FILE_SIZE_EXCEEDED"));
        }
        return Mono.just(fileData);
    }

    private Mono<FileData> validateExtension(FileData fileData) {
        String extension = fileData.extension();

        if (!allowedTypes.contains(extension.toLowerCase())) {
            log.warn("File {} has invalid extension: {}",
                fileData.getFilename(), extension);
            return Mono.error(new FileValidationException(
                "File type not allowed. Allowed types: " + config.allowedTypes(),
                "INVALID_FILE_TYPE"));
        }
        return Mono.just(fileData);
    }

    private Mono<FileData> validateFilename(FileData fileData) {
        String filename = fileData.getFilename();
        if (filename.length() > config.maxFilenameLength()) {
            log.warn("Filename exceeds max length: {}", filename.length());
            return Mono.error(new FileValidationException(
                "Filename exceeds maximum length: " + config.maxFilenameLength(),
                "FILENAME_TOO_LONG"));
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Filename contains invalid characters: {}", filename);
            return Mono.error(new FileValidationException(
                "Filename contains invalid characters",
                "INVALID_FILENAME"));
        }
        return Mono.just(fileData);
    }
}