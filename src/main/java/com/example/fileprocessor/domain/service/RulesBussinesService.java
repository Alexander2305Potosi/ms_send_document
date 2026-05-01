package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Default implementation of document validation gateway.
 * Applies validation rules to incoming documents based on configuration.
 */
public class RulesBussinesService implements RulesBussinesGateway {

    private static final Logger log = LoggerFactory.getLogger(RulesBussinesService.class);

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
    public Mono<ProductDocument> validate(ProductDocument doc) {
        return Mono.defer(() -> {
            if (maxFileSizeBytes != null && doc.size() > maxFileSizeBytes) {
                log.debug("Document {} skipped: size {} exceeds max {}",
                    doc.documentId(), doc.size(), maxFileSizeBytes);
                return Mono.empty();
            }
            if (filenamePattern != null && !filenamePattern.matcher(doc.filename()).matches()) {
                log.debug("Document {} skipped: filename {} does not match pattern {}",
                    doc.documentId(), doc.filename(), filenamePattern.pattern());
                return Mono.empty();
            }
            return Mono.just(doc);
        });
    }
}
