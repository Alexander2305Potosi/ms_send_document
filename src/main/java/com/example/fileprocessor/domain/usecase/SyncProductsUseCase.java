package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.ProductPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Use case for syncing products from REST API to local database.
 */
@Component
public class SyncProductsUseCase {

    private final ProductRestGateway productRestGateway;
    private final ProductPersistenceGateway productPersistenceGateway;

    public SyncProductsUseCase(
            ProductRestGateway productRestGateway,
            ProductPersistenceGateway productPersistenceGateway) {
        this.productRestGateway = productRestGateway;
        this.productPersistenceGateway = productPersistenceGateway;
    }

    public Mono<Void> execute() {
        return productRestGateway.getAllProducts()
            .flatMap(this::saveProduct)
            .then();
    }

    private Mono<Void> saveProduct(Product product) {
        Product productToSave = new Product(
            product.productId(),
            product.name(),
            LocalDateTime.now(),
            ProductState.PENDING,
            null,
            product.documents()
        );
        return productPersistenceGateway.save(productToSave);
    }
}
