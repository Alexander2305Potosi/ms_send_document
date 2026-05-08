package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DocumentValidatorTest {

    private static ProductDocumentHistory doc(String documentId, String name, String contentType, long size) {
        return ProductDocumentHistory.builder()
            .productId("prod-1")
            .isZip(false)
            .pais("AR")
            .documentId(documentId)
            .name(name)
            .filename(name)
            .contentType(contentType)
            .size(size)
            .origin("origin")
            .content(new byte[0])
            .build();
    }

    private static ProcessorsProperties.ProcessorConfig config(Long maxFileSizeBytes, String filenamePattern) {
        return new ProcessorsProperties.ProcessorConfig(maxFileSizeBytes, filenamePattern);
    }

    @Test
    void validate_singleRule_passes() {
        RulesBussinesService validator = new RulesBussinesService(
            config(null, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validate_patternFails() {
        RulesBussinesService validator = new RulesBussinesService(
            config(null, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.csv", "application/pdf", 500)))
            .verifyError(ProcessingException.class);
    }

    @Test
    void validate_multipleRules_allPass() {
        RulesBussinesService validator = new RulesBussinesService(
            config(null, ".*\\.(pdf|docx)$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validate_multipleRules_oneFails() {
        RulesBussinesService validator = new RulesBussinesService(
            config(null, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.csv", "application/pdf", 500)))
            .verifyError(ProcessingException.class);
    }

    @Test
    void validate_noPattern_passes() {
        RulesBussinesService validator = new RulesBussinesService(
            config(null, null)
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 999999)))
            .expectNextCount(1)
            .verifyComplete();
    }
}