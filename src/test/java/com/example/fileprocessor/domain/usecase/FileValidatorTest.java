package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FileValidatorTest {

    @Mock
    private FileValidationConfig config;

    private FileValidator fileValidator;

    @BeforeEach
    void setUp() {
        lenient().when(config.allowedTypes()).thenReturn("pdf,txt,csv");
        lenient().when(config.maxSize()).thenReturn(50 * 1024 * 1024L);
        lenient().when(config.maxFilenameLength()).thenReturn(255);
        fileValidator = new FileValidator(config);
    }

    @Test
    void validate_withValidFile_shouldReturnFileData() {
        FileData fileData = FileData.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .size(3)
            .contentType("application/pdf")
            .traceId("trace-1")
            .build();

        Mono<FileData> result = fileValidator.validate(fileData);

        StepVerifier.create(result)
            .expectNextMatches(data -> data.getFilename().equals("test.pdf"))
            .verifyComplete();
    }

    @Test
    void validate_withInvalidExtension_shouldReturnError() {
        FileData fileData = FileData.builder()
            .documentId("doc-1")
            .filename("test.exe")
            .content(new byte[]{1, 2, 3})
            .size(3)
            .contentType("application/octet-stream")
            .traceId("trace-1")
            .build();

        Mono<FileData> result = fileValidator.validate(fileData);

        StepVerifier.create(result)
            .expectErrorMatches(e -> e instanceof FileValidationException)
            .verify();
    }

    @Test
    void validate_withFilenameTooLong_shouldReturnError() {
        String longFilename = "a".repeat(300) + ".txt";
        FileData fileData = FileData.builder()
            .documentId("doc-1")
            .filename(longFilename)
            .content(new byte[]{1, 2, 3})
            .size(3)
            .contentType("text/plain")
            .traceId("trace-1")
            .build();

        Mono<FileData> result = fileValidator.validate(fileData);

        StepVerifier.create(result)
            .expectErrorMatches(e -> e instanceof FileValidationException)
            .verify();
    }
}