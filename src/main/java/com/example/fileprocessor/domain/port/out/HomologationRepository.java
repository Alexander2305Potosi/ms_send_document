package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.HomologationResult;
import reactor.core.publisher.Mono;

/**
 * Port for resolving document homologations.
 * Combines CategoryManual and PaisHomologado lookups.
 */
public interface HomologationRepository {
    Mono<HomologationResult> resolve(String origin, String pais);
}