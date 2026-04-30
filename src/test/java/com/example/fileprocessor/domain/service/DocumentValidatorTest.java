package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties.ProcessorConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class DocumentValidatorTest {

    private static ProductDocument doc(String documentId, String filename, long size) {
        return new ProductDocument(documentId, filename, new byte[0], "application/octet-stream", size, false, "origin");
    }

    @Test
    void validate_whenAllValid_passes() {
        ProcessorConfig config = new ProcessorConfig(52428800L, ".*\\.pdf$");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.pdf", 1024);

        StepVerifier.create(validator.validate(doc))
            .expectNext(doc)
            .verifyComplete();
    }

    @Test
    void validate_whenSizeExceedsLimit_rejects() {
        ProcessorConfig config = new ProcessorConfig(1000L, ".*");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.pdf", 5000);

        StepVerifier.create(validator.validate(doc))
            .verifyComplete();
    }

    @Test
    void validate_whenSizeWithinLimit_passes() {
        ProcessorConfig config = new ProcessorConfig(10000L, ".*");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.pdf", 5000);

        StepVerifier.create(validator.validate(doc))
            .expectNextMatches(d -> d.documentId().equals("doc-1"))
            .verifyComplete();
    }

    @Test
    void validate_whenFilenameDoesNotMatchPattern_rejects() {
        ProcessorConfig config = new ProcessorConfig(null, ".*\\.pdf$");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.csv", 1024);

        StepVerifier.create(validator.validate(doc))
            .verifyComplete();
    }

    @Test
    void validate_whenFilenameMatchesPattern_passes() {
        ProcessorConfig config = new ProcessorConfig(null, ".*\\.pdf$");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.pdf", 1024);

        StepVerifier.create(validator.validate(doc))
            .expectNext(doc)
            .verifyComplete();
    }

    @Test
    void validate_whenBothRulesNull_passes() {
        ProcessorConfig config = new ProcessorConfig(null, null);
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "anything.anything", 999999999L);

        StepVerifier.create(validator.validate(doc))
            .expectNext(doc)
            .verifyComplete();
    }

    @Test
    void validate_whenSizeZero_noSizeValidation() {
        ProcessorConfig config = new ProcessorConfig(0L, ".*");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.pdf", 999999999L);

        StepVerifier.create(validator.validate(doc))
            .expectNext(doc)
            .verifyComplete();
    }

    @Test
    void validate_whenFilenameEmpty_noFilenameValidation() {
        ProcessorConfig config = new ProcessorConfig(1000L, "");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "anything.anything", 500);

        StepVerifier.create(validator.validate(doc))
            .expectNext(doc)
            .verifyComplete();
    }

    @Test
    void validate_whenBothRulesFail_rejects() {
        ProcessorConfig config = new ProcessorConfig(1000L, ".*\\.pdf$");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument doc = doc("doc-1", "test.csv", 5000);

        StepVerifier.create(validator.validate(doc))
            .verifyComplete();
    }

    @Test
    void validate_sizeLimitInclusive() {
        ProcessorConfig config = new ProcessorConfig(1000L, ".*");
        DocumentValidator validator = new DocumentValidator(config);

        ProductDocument docAtLimit = doc("doc-1", "test.pdf", 1000);

        StepVerifier.create(validator.validate(docAtLimit))
            .expectNext(docAtLimit)
            .verifyComplete();
    }
}
