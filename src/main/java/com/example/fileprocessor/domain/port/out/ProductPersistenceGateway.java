package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Product;
import reactor.core.publisher.Mono;

/**
 * Gateway for persisting products to local database.
 */
public interface ProductPersistenceGateway {
    Mono<Void> save(Product product);
}
