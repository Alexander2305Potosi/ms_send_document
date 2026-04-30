package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator(1.0, "pdf,txt,csv");
    }

    @Test
    void validate_shouldPassForValidDocument() {
        ProductDocumentToProcess doc = createDocumentWithSize("doc-1", "valid.pdf", 0.5);

        StepVerifier.create(validator.validate(doc))
            .expectNextMatches(result -> result.getDocumentId().equals("doc-1"))
            .verifyComplete();
    }

    @Test
    void validate_shouldRejectFileTooLarge() {
        ProductDocumentToProcess doc = createDocumentWithSize("doc-2", "large.pdf", 5.0);

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("exceeds limit"))
            .verify();
    }

    @Test
    void validate_shouldRejectInvalidExtension() {
        ProductDocumentToProcess doc = createDocumentWithSize("doc-3", "document.doc", 0.5);

        StepVerifier.create(validator.validate(doc))
            .expectErrorMatches(ex -> ex instanceof FileValidationException
                && ex.getMessage().contains("not allowed"))
            .verify();
    }

    private ProductDocumentToProcess createDocumentWithSize(String docId, String filename, double fileSizeMb) {
        return ProductDocumentToProcess.builder()
            .documentId(docId)
            .productId("prod-1")
            .filename(filename)
            .content(new byte[]{1})
            .contentType("application/pdf")
            .origin("/incoming/" + filename)
            .createdAt(Instant.now())
            .fileSizeMb(fileSizeMb)
            .build();
    }
}
