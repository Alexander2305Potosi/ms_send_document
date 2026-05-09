package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Port for product repository operations.
 */
public interface ProductRepository {
    Flux<ProductHistory> findByLoadDate(LocalDate loadDate);
    Flux<ProductHistory> findAll();
    Mono<ProductHistory> save(ProductHistory product);
    Mono<Void> updateEstadoById(Long id, String estado);
}