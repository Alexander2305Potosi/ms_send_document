package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import reactor.core.publisher.Mono;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Default implementation of document validation gateway.
 * Applies validation rules to incoming documents based on configuration.
 *
 * <p>During sync (API_V1_PRODUCTS_SYNC): only name pattern validation is applied.
 * During processing (API_V1_PRODUCTS): size and name validations are both applied.
 */
public class RulesBussinesService implements RulesBussinesGateway {

    private static final Logger log = Logger.getLogger(RulesBussinesService.class.getName());

    private final Long maxFileSizeBytes;
    private final Pattern filenamePattern;

    public RulesBussinesService(ProcessorsProperties.ProcessorConfig config) {
        this.maxFileSizeBytes = (config.maxFileSizeBytes() != null && config.maxFileSizeBytes() > 0)
            ? config.maxFileSizeBytes()
            : null;
        this.filenamePattern = (config.filenamePattern() != null && !config.filenamePattern().isBlank())
            ? Pattern.compile(config.filenamePattern())
            : null;
    }

    @Override
    public Mono<ProductDocumentHistory> validate(ProductDocumentHistory doc) {
        return validate(doc, false);
    }

    public Mono<ProductDocumentHistory> validate(ProductDocumentHistory doc, boolean includeSizeCheck) {
        return Mono.defer(() -> {
            if (includeSizeCheck && maxFileSizeBytes != null && doc.size() != null && doc.size() > maxFileSizeBytes) {
                log.log(Level.FINE, "Document {0} skipped: size {1} exceeds max {2}",
                    new Object[]{doc.documentId(), doc.size(), maxFileSizeBytes});
                return Mono.empty();
            }
            if (filenamePattern != null && !filenamePattern.matcher(doc.name()).matches()) {
                log.log(Level.FINE, "Document {0} skipped: name {1} does not match pattern {2}",
                    new Object[]{doc.documentId(), doc.name(), filenamePattern.pattern()});
                return Mono.empty();
            }
            return Mono.just(doc);
        });
    }
}