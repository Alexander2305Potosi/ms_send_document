package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Product;
import reactor.core.publisher.Flux;

/**
 * Port for querying products from local database.
 */
public interface ProductDbGateway {
    Flux<Product> findByLoadDate(java.time.LocalDate loadDate);
    Flux<Product> findAll();
}
