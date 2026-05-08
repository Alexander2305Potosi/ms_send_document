package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Default implementation of document validation gateway.
 * Applies validation rules to incoming documents based on configuration.
 *
 * <p>During sync (API_V1_PRODUCTS_SYNC): only name pattern validation is applied.
 * During processing (API_V1_PRODUCTS): size and name validations are both applied.
 */
public class RulesBussinesService implements RulesBussinesGateway {

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
                return Mono.error(new ProcessingException(
                    ProcessingResultCodes.SIZE_EXCEEDED,
                    String.format("Size %,d bytes exceeds max %,d bytes for file '%s'",
                        doc.size(), maxFileSizeBytes, doc.filename())));
            }
            if (filenamePattern != null && !filenamePattern.matcher(doc.name()).matches()) {
                return Mono.error(new ProcessingException(
                    ProcessingResultCodes.PATTERN_MISMATCH,
                    String.format("Filename '%s' does not match pattern '%s'",
                        doc.name(), filenamePattern.pattern())));
            }
            return Mono.just(doc);
        });
    }
}