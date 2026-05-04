package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DocumentValidatorTest {

    private static ProductDocumentHistory doc(String documentId, String filename, String contentType, long size) {
        return new ProductDocumentHistory(documentId, filename, new byte[0], contentType, size, false, "origin", "AR");
    }

    private static ProcessorsProperties.ProcessorConfig config(Long maxFileSizeBytes, String filenamePattern) {
        return new ProcessorsProperties.ProcessorConfig(maxFileSizeBytes, filenamePattern);
    }

    @Test
    void validate_singleRule_passes() {
        RulesBussinesService validator = new RulesBussinesService(
            config(1000L, null)
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validate_singleRule_fails() {
        RulesBussinesService validator = new RulesBussinesService(
            config(1000L, null)
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 2000)))
            .verifyComplete();
    }

    @Test
    void validate_multipleRules_allPass() {
        RulesBussinesService validator = new RulesBussinesService(
            config(1000L, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validate_multipleRules_oneFails() {
        RulesBussinesService validator = new RulesBussinesService(
            config(1000L, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.csv", "application/pdf", 500)))
            .verifyComplete();
    }

    @Test
    void validate_emptyConfig_passes() {
        RulesBussinesService validator = new RulesBussinesService(
            config(null, null)
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 999999)))
            .expectNextCount(1)
            .verifyComplete();
    }
}
