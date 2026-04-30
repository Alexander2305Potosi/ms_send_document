package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.BussinesParamsGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.BussinesParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

public class DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidator.class);

    private final BussinesParamsGateway bussinesParamsGateway;

    public DocumentValidator(BussinesParamsGateway bussinesParamsGateway) {
        this.bussinesParamsGateway = bussinesParamsGateway;
    }

    public Mono<ProductDocument> validate(ProductDocument doc) {
        return Mono.defer(() -> {
            if (!validateFileSize(doc)) {
                log.warn("Document {} skipped - file size {} exceeds limit of {} bytes",
                    doc.documentId(), doc.size(), getMaxFileSize());
                return Mono.empty();
            }
            return validateFilenamePattern(doc);
        });
    }

    private boolean validateFileSize(ProductDocument doc) {
        long maxFileSize = getMaxFileSize();
        if (maxFileSize > 0 && doc.size() > maxFileSize) {
            return false;
        }
        return true;
    }

    private Mono<ProductDocument> validateFilenamePattern(ProductDocument doc) {
        String filename = doc.filename();
        var filenamePattern = Pattern.compile(bussinesParamsGateway.getValue(BussinesParams.REGEX));
        if (filename != null && filenamePattern.matcher(filename).find()) {
            return Mono.just(doc);
        }
        log.debug("Document {} skipped - filename does not match pattern", doc.documentId());
        return Mono.empty();
    }

    private long getMaxFileSize() {
        String maxFileSize = bussinesParamsGateway.getValue(BussinesParams.MAX_FILE_SIZE);
        if (maxFileSize != null && !maxFileSize.isBlank()) {
            return Long.parseLong(maxFileSize);
        }
        return 0;
    }
}
