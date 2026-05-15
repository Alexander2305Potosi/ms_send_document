package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.DocumentHistory;
import reactor.core.publisher.Mono;

/**
 * Gateway interface for document validation.
 */
public interface RulesBussinesGateway {

    Mono<DocumentHistory> validate(DocumentHistory history);

    default Mono<DocumentHistory> validate(DocumentHistory history, boolean includeSizeCheck) {
        return validate(history);
    }
}
