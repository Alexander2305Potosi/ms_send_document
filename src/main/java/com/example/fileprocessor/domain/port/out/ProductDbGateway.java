package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for querying products from local database.
 */
public interface ProductDbGateway {
    Flux<Product> findByLoadDate(java.time.LocalDate loadDate);
    Mono<Void> updateEstado(String productId, String estado);
    Flux<Product> findAll();
}
