package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductHistory;
import reactor.core.publisher.Flux;

/**
 * Port for fetching master product information from an external database.
 * Strictly for database-related operations.
 */
public interface ProductMasterRepository {
    Flux<ProductHistory> getAllProducts();
}
