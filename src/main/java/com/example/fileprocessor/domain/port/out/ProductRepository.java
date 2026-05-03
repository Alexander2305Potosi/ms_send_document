package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for product repository operations.
 */
public interface ProductRepository {
    Flux<Product> findByLoadDate(java.time.LocalDate loadDate);
    Flux<Product> findAll();
    Mono<Void> save(Product product);
    Mono<Void> updateEstado(String productId, String estado);
    Mono<Void> updateEstadoById(Long id, String estado);
}