package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.service.rules.FilenamePatternRule;
import com.example.fileprocessor.domain.service.rules.MaxSizeRule;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

class DocumentValidatorTest {

    private static ProductDocument doc(String documentId, String filename, String contentType, long size) {
        return new ProductDocument(documentId, filename, new byte[0], contentType, size, false, "origin");
    }

    @Test
    void validate_singleRule_passes() {
        DefaultDocumentValidationService validator = new DefaultDocumentValidationService(List.of(
            new MaxSizeRule(1000L)
        ));

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validate_singleRule_fails() {
        DefaultDocumentValidationService validator = new DefaultDocumentValidationService(List.of(
            new MaxSizeRule(1000L)
        ));

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 2000)))
            .verifyComplete();
    }

    @Test
    void validate_multipleRules_allPass() {
        DefaultDocumentValidationService validator = new DefaultDocumentValidationService(List.of(
            new MaxSizeRule(1000L),
            new FilenamePatternRule(".*\\.pdf$")
        ));

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validate_multipleRules_oneFails() {
        DefaultDocumentValidationService validator = new DefaultDocumentValidationService(List.of(
            new MaxSizeRule(1000L),
            new FilenamePatternRule(".*\\.pdf$")
        ));

        StepVerifier.create(validator.validate(doc("doc-1", "test.csv", "application/pdf", 500)))
            .verifyComplete();
    }

    @Test
    void validate_emptyRulesList_passes() {
        DefaultDocumentValidationService validator = new DefaultDocumentValidationService(List.of());

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 999999)))
            .expectNextCount(1)
            .verifyComplete();
    }
}
