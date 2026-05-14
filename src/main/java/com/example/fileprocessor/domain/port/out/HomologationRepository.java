package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.HomologationResult;
import reactor.core.publisher.Mono;

public interface HomologationRepository {
    /**
     * Resuelve y valida la homologación basada en el origen y el país.
     * Implementa lógica de caché y validación interna.
     */
    Mono<HomologationResult> resolve(String origin, String pais);
}
