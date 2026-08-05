package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DocumentValidatorTest {

    private static DocumentHistoryDTO doc(String documentId, String name, String contentType, long size) {
        return DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(false)
            .originCountry("AR")
            .businessDocumentId(documentId)
            .filename(name)
            .contentType(contentType)
            .size(size)
            .originFolder("origin")
            .build();
    }

    private static ProcessorsProperties.ProcessorConfig config(Long maxFileSizeBytes, String filenamePattern) {
        return new ProcessorsProperties.ProcessorConfig(maxFileSizeBytes, filenamePattern);
    }

    @Test
    void validateSingleRulePasses() {
        RulesBussinesService<DocumentHistoryDTO> validator = new RulesBussinesService<>(
            config(null, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validatePatternFails() {
        RulesBussinesService<DocumentHistoryDTO> validator = new RulesBussinesService<>(
            config(null, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.csv", "application/pdf", 500)))
            .verifyError(ProcessingException.class);
    }

    @Test
    void validateMultipleRulesAllPass() {
        RulesBussinesService<DocumentHistoryDTO> validator = new RulesBussinesService<>(
            config(null, ".*\\.(pdf|docx)$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500)))
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void validateMultipleRulesOneFails() {
        RulesBussinesService<DocumentHistoryDTO> validator = new RulesBussinesService<>(
            config(null, ".*\\.pdf$")
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.csv", "application/pdf", 500)))
            .verifyError(ProcessingException.class);
    }

    @Test
    void validateSizeExceededThrowsProcessingException() {
        RulesBussinesService<DocumentHistoryDTO> validator = new RulesBussinesService<>(
            config(100L, null)
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 500), true))
            .verifyError(ProcessingException.class);
    }

    @Test
    void validateNoPatternPasses() {
        RulesBussinesService<DocumentHistoryDTO> validator = new RulesBussinesService<>(
            config(null, null)
        );

        StepVerifier.create(validator.validate(doc("doc-1", "test.pdf", "application/pdf", 999999)))
            .expectNextCount(1)
            .verifyComplete();
    }
}