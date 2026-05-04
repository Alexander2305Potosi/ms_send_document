package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import reactor.core.publisher.Mono;

/**
 * Gateway interface for document validation.
 * Abstracts the validation implementation from domain logic.
 */
public interface RulesBussinesGateway {

    Mono<ProductDocumentHistory> validate(ProductDocumentHistory doc);
}
