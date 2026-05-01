package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.DocumentValidationGateway;
import com.example.fileprocessor.domain.service.rules.FilenamePatternRule;
import com.example.fileprocessor.domain.service.rules.MaxSizeRule;
import com.example.fileprocessor.domain.service.ValidationRule;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of document validation gateway.
 * Applies all configured ValidationRules to incoming documents.
 */
public class DefaultDocumentValidationService implements DocumentValidationGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentValidationService.class);

    private final List<ValidationRule> rules;

    /**
     * Constructor accepting pre-built rules list.
     */
    public DefaultDocumentValidationService(List<ValidationRule> rules) {
        this.rules = rules;
    }

    /**
     * Constructor accepting processor configuration.
     * Builds rules from configuration properties.
     */
    public DefaultDocumentValidationService(ProcessorsProperties.ProcessorConfig config) {
        this.rules = buildRules(config);
    }

    private static List<ValidationRule> buildRules(ProcessorsProperties.ProcessorConfig config) {
        List<ValidationRule> rules = new ArrayList<>();

        if (config.maxFileSizeBytes() != null && config.maxFileSizeBytes() > 0) {
            rules.add(new MaxSizeRule(config.maxFileSizeBytes()));
        }

        if (config.filenamePattern() != null && !config.filenamePattern().isBlank()) {
            rules.add(new FilenamePatternRule(config.filenamePattern()));
        }

        return rules;
    }

    @Override
    public Mono<ProductDocument> validate(ProductDocument doc) {
        return Mono.defer(() -> {
            for (ValidationRule rule : rules) {
                if (!rule.isValid(doc)) {
                    log.debug("Document {} skipped: {}",
                        doc.documentId(), rule.reasonIfInvalid(doc));
                    return Mono.empty();
                }
            }
            return Mono.just(doc);
        });
    }
}
