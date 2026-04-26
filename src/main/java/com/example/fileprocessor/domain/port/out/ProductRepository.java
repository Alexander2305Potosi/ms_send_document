package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductToProcess;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for product persistence operations.
 * Abstraction that allows different storage implementations (R2DBC, JPA, etc.)
 */
public interface ProductRepository {
    /**
     * Finds all products with pending status.
     * @return Flux of products awaiting processing
     */
    Flux<ProductToProcess> findPendingProducts();

    /**
     * Saves a product to storage.
     * @param product the product to save
     * @return Mono that completes when saved
     */
    Mono<Void> save(ProductToProcess product);

    /**
     * Updates the status of a product.
     * @param productId the product identifier
     * @param status the new status
     * @param traceId the trace identifier for logging
     * @return Mono that completes when updated
     */
    Mono<Void> updateStatus(String productId, String status, String traceId);
}
