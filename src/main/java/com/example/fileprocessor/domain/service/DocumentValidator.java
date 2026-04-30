package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

public class DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidator.class);

    private final long maxFileSizeBytes;
    private final Pattern filenamePattern;

    public DocumentValidator(long maxFileSizeBytes, String filenamePattern) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.filenamePattern = Pattern.compile(filenamePattern);
    }

    public Mono<ProductDocument> validate(ProductDocument doc) {
        return Mono.defer(() -> {
            if (maxFileSizeBytes > 0 && doc.size() > maxFileSizeBytes) {
                log.warn("Document {} skipped - file size {} exceeds limit of {} bytes",
                    doc.documentId(), doc.size(), maxFileSizeBytes);
                return Mono.empty();
            }
            if (!filenamePattern.matcher(doc.filename()).matches()) {
                log.debug("Document {} skipped - filename '{}' does not match pattern",
                    doc.documentId(), doc.filename());
                return Mono.empty();
            }
            return Mono.just(doc);
        });
    }
}
