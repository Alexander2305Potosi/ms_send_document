package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileValidatorTest {

    @Mock
    private FileValidationConfig config;

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        when(config.maxSize()).thenReturn(1000L);
        when(config.allowedTypes()).thenReturn("pdf,txt,csv");
        when(config.maxFilenameLength()).thenReturn(100);
        when(config.maxFileSizeMb()).thenReturn(10);
        when(config.contentTypePatterns()).thenReturn(java.util.List.of());
        when(config.allowedContentTypeTokens()).thenReturn(java.util.Set.of());
        when(config.shouldValidateContentType()).thenReturn(true);
        when(config.shouldValidateSize()).thenReturn(true);
        when(config.shouldValidateExtension()).thenReturn(true);
        when(config.shouldValidateFilename()).thenReturn(true);
        validator = new FileValidator(config);
    }

    @Test
    void validate_shouldPassForValidDocument() {
        ProductDocumentToProcess doc = createDocument("doc-1", "valid.pdf");

        StepVerifier.create(validator.validate(doc))
            .expectNextMatches(result -> result.getDocumentId().equals("doc-1"))
            .verifyComplete();
    }

    @Test
    void validate_shouldRejectFileTooLarge() {
        ProductDocumentToProcess doc = createDocumentWithSize("doc-2", "large.pdf", 2000);

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("exceeds limit"))
            .verify();
    }

    @Test
    void validate_shouldRejectInvalidExtension() {
        ProductDocumentToProcess doc = createDocument("doc-3", "document.doc");

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("not allowed"))
            .verify();
    }

    @Test
    void validate_shouldRejectFilenameTooLong() {
        String longName = "a".repeat(150) + ".pdf";
        ProductDocumentToProcess doc = createDocument("doc-4", longName);

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("exceeds max"))
            .verify();
    }

    @Test
    void validate_shouldRejectPathTraversal() {
        ProductDocumentToProcess doc = createDocument("doc-5", "../../../etc/passwd.pdf");

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("invalid path"))
            .verify();
    }

    @Test
    void validate_shouldRejectBackslashInFilename() {
        ProductDocumentToProcess doc = createDocument("doc-6", "file\\name.pdf");

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("invalid path"))
            .verify();
    }

    @Test
    void validate_shouldRejectSlashInFilename() {
        ProductDocumentToProcess doc = createDocument("doc-7", "folder/file.pdf");

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("invalid path"))
            .verify();
    }

    private ProductDocumentToProcess createDocument(String docId, String filename) {
        return ProductDocumentToProcess.builder()
            .documentId(docId)
            .productId("prod-1")
            .filename(filename)
            .content(new byte[]{1})
            .contentType("application/pdf")
            .origin("/incoming/" + filename)
            .traceId("trace-" + docId)
            .createdAt(Instant.now())
            .build();
    }

    private ProductDocumentToProcess createDocumentWithSize(String docId, String filename, int size) {
        byte[] content = new byte[size];
        return ProductDocumentToProcess.builder()
            .documentId(docId)
            .productId("prod-1")
            .filename(filename)
            .content(content)
            .contentType("application/pdf")
            .origin("/incoming/" + filename)
            .traceId("trace-" + docId)
            .createdAt(Instant.now())
            .build();
    }
}
