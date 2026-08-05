package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import reactor.core.publisher.Mono;

/**
 * Generic gateway interface for document validation.
 */
public interface RulesBussinesGateway<H extends BaseDocumentHistoryDTO> {

    Mono<H> validate(H history);

    default Mono<H> validate(H history, boolean includeSizeCheck) {
        return validate(history);
    }
}
