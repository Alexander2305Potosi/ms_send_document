package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import reactor.core.publisher.Mono;

/**
 * Domain gateway for homologation and routing rules.
 * Parametrized to support any subclass of BaseDocumentHistoryDTO.
 */
public interface HomologationRepository {
    /**
     * Resuelve y valida la homologación basada en el origen y el país.
     * Implementa lógica de caché y validación interna.
     */
    Mono<HomologationResult> resolve(BaseDocumentHistoryDTO history);
}
