package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.infrastructure.config.FileUploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class FileValidator {

    private static final Logger log = LoggerFactory.getLogger(FileValidator.class);
    private final FileUploadProperties properties;

    public FileValidator(FileUploadProperties properties) {
        this.properties = properties;
    }

    public Mono<FileData> validate(FileData fileData) {
        return Mono.just(fileData)
            .flatMap(this::validateSize)
            .flatMap(this::validateExtension)
            .flatMap(this::validateFilename);
    }

    private Mono<FileData> validateSize(FileData fileData) {
        if (fileData.size() > properties.maxSize()) {
            log.warn("File {} exceeds max size: {} > {}",
                fileData.filename(), fileData.size(), properties.maxSize());
            return Mono.error(new FileValidationException(
                "File size exceeds maximum allowed: " + properties.maxSize() + " bytes",
                "FILE_SIZE_EXCEEDED"));
        }
        return Mono.just(fileData);
    }

    private Mono<FileData> validateExtension(FileData fileData) {
        String extension = fileData.extension();
        Set<String> allowedTypes = new HashSet<>(
            Arrays.asList(properties.allowedTypes().split(",")));

        if (!allowedTypes.contains(extension.toLowerCase())) {
            log.warn("File {} has invalid extension: {}",
                fileData.filename(), extension);
            return Mono.error(new FileValidationException(
                "File type not allowed. Allowed types: " + properties.allowedTypes(),
                "INVALID_FILE_TYPE"));
        }
        return Mono.just(fileData);
    }

    private Mono<FileData> validateFilename(FileData fileData) {
        String filename = fileData.filename();
        if (filename.length() > properties.maxFilenameLength()) {
            log.warn("Filename exceeds max length: {}", filename.length());
            return Mono.error(new FileValidationException(
                "Filename exceeds maximum length: " + properties.maxFilenameLength(),
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
