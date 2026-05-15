package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import reactor.core.publisher.Flux;

/**
 * Port for fetching master product information from an external database.
 */
public interface ProductMasterRepository {
    Flux<ProductMaestro> getAllProducts();
}
