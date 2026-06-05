package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import reactor.core.publisher.Mono;

/**
 * Gateway interface for document validation.
 */
public interface RulesBussinesGateway {

    Mono<DocumentHistoryDTO> validate(DocumentHistoryDTO history);

    default Mono<DocumentHistoryDTO> validate(DocumentHistoryDTO history, boolean includeSizeCheck) {
        return validate(history);
    }
}
