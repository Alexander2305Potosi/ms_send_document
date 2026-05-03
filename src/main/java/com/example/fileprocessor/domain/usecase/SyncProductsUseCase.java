package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.ProductRepository;
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
    private final ProductRepository productRepository;

    public SyncProductsUseCase(
            ProductRestGateway productRestGateway,
            ProductRepository productRepository) {
        this.productRestGateway = productRestGateway;
        this.productRepository = productRepository;
    }

    public Mono<Void> execute() {
        return productRestGateway.getAllProducts()
            .flatMap(this::saveProduct)
            .then();
    }

    private Mono<Void> saveProduct(Product product) {
        Product productToSave = new Product(
            null,
            product.productId(),
            product.name(),
            LocalDateTime.now(),
            ProductState.PENDING,
            null,
            product.documents()
        );
        return productRepository.save(productToSave);
    }
}