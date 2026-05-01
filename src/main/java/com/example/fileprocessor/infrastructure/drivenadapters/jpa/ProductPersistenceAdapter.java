package com.example.fileprocessor.infrastructure.drivenadapters.jpa;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.ProductPersistenceGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity.PendingProductEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.jpa.repository.PendingProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Component
public class ProductPersistenceAdapter implements ProductPersistenceGateway {

    private static final Logger log = LoggerFactory.getLogger(ProductPersistenceAdapter.class);

    private final PendingProductRepository repository;

    public ProductPersistenceAdapter(PendingProductRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Mono<Void> save(Product product) {
        return Mono.fromRunnable(() -> {
            PendingProductEntity entity = new PendingProductEntity(
                product.productId(),
                product.name(),
                product.loadDate(),
                product.state()
            );
            repository.save(entity);
            log.info("Product {} saved with {} state", product.productId(), product.state());
        });
    }
}
