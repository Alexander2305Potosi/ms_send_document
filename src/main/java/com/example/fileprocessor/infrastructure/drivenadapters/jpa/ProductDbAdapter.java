package com.example.fileprocessor.infrastructure.drivenadapters.jpa;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.ProductDbGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity.PendingProductEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.jpa.repository.PendingProductRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class ProductDbAdapter implements ProductDbGateway {

    private final PendingProductRepository repository;

    public ProductDbAdapter(PendingProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Product> findByLoadDate(LocalDate loadDate) {
        LocalDateTime startOfDay = loadDate.atStartOfDay();
        LocalDateTime endOfDay = loadDate.atTime(LocalTime.MAX);
        return Flux.fromIterable(
                repository.findByLoadDateBetweenAndState(startOfDay, endOfDay, ProductState.PENDING))
            .map(this::toProduct);
    }

    @Override
    public Flux<Product> findAll() {
        return Flux.fromIterable(repository.findAll())
            .map(this::toProduct);
    }

    private Product toProduct(PendingProductEntity entity) {
        return Product.builder()
            .productId(entity.getProductId())
            .name(entity.getName())
            .loadDate(entity.getLoadDate())
            .state(entity.getState())
            .messageError(entity.getMessageError())
            .build();
    }
}
