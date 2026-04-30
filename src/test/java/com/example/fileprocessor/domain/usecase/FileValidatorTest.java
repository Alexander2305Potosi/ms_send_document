package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.usecase.FileValidator.FolderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FileValidatorTest {

    @Mock
    private FileValidationConfig config;

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(config.maxSize()).thenReturn(1000L);
        lenient().when(config.allowedTypes()).thenReturn("pdf,txt,csv");
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
    void extractFolderInfo_shouldReturnDefaultWhenNoKeywords() {
        when(config.keywords()).thenReturn(java.util.List.of());

        FolderInfo info = validator.extractFolderInfo("/incoming/file.pdf");

        assertEquals(".", info.parentFolder());
        assertEquals(".", info.childFolder());
    }

    @Test
    void extractFolderInfo_shouldExtractParentAndChildFromMatchingKeyword() {
        when(config.keywords()).thenReturn(java.util.List.of("incoming"));

        FolderInfo info = validator.extractFolderInfo("/incoming/documents/file.pdf");

        assertEquals("documents", info.parentFolder());
        assertEquals("file.pdf", info.childFolder());
    }

    private ProductDocumentToProcess createDocument(String docId, String filename) {
        return ProductDocumentToProcess.builder()
            .documentId(docId)
            .productId("prod-1")
            .filename(filename)
            .content(new byte[]{1})
            .contentType("application/pdf")
            .origin("/incoming/" + filename)
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
            .createdAt(Instant.now())
            .build();
    }
}