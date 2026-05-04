package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for product repository operations.
 */
public interface ProductRepository {
    Flux<ProductHistory> findByLoadDate(java.time.LocalDate loadDate);
    Flux<ProductHistory> findAll();
    Mono<Void> save(ProductHistory product);
    Mono<Void> updateEstado(String productId, String estado);
    Mono<Void> updateEstadoById(Long id, String estado);
}