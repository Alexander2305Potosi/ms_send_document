package com.example.fileprocessor.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Port for querying product branch information from local master tables.
 */
public interface ProductLocalRepository {
    Mono<String> findBranchByProductId(String productId);
}
