package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties.ProcessorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

public class DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidator.class);

    private final ProcessorConfig config;

    public DocumentValidator(ProcessorConfig config) {
        this.config = config;
    }

    public Mono<ProductDocument> validate(ProductDocument doc) {
        return Mono.defer(() -> {
            if (config.maxFileSizeBytes() != null && config.maxFileSizeBytes() > 0) {
                if (doc.size() > config.maxFileSizeBytes()) {
                    log.warn("Document {} skipped - file size {} exceeds limit of {} bytes",
                        doc.documentId(), doc.size(), config.maxFileSizeBytes());
                    return Mono.empty();
                }
            }

            if (config.filenamePattern() != null && !config.filenamePattern().isBlank()) {
                if (!Pattern.matches(config.filenamePattern(), doc.filename())) {
                    log.debug("Document {} skipped - filename '{}' does not match pattern",
                        doc.documentId(), doc.filename());
                    return Mono.empty();
                }
            }

            return Mono.just(doc);
        });
    }
}
