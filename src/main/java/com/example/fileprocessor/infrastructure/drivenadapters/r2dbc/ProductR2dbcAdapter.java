package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.ProductMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class ProductR2dbcAdapter implements ProductRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.ProductRepository repository;

    public ProductR2dbcAdapter(com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Product> findByLoadDate(LocalDate loadDate) {
        LocalDateTime startOfDay = loadDate.atStartOfDay();
        LocalDateTime endOfDay = loadDate.atTime(LocalTime.MAX);
        return repository.findByLoadDateBetween(startOfDay, endOfDay)
            .filter(entity -> "PENDING".equals(entity.getState()))
            .map(ProductMapper::toDomain);
    }

    @Override
    public Flux<Product> findAll() {
        return repository.findAll()
            .map(ProductMapper::toDomain);
    }

    @Override
    public Mono<Void> save(Product product) {
        return repository.save(ProductMapper.toEntity(product))
            .then();
    }

    @Override
    public Mono<Void> updateEstado(String productId, String estado) {
        return repository.findByProductId(productId)
            .flatMap(entity -> {
                entity.setState(estado);
                return repository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<Void> updateEstadoById(Long id, String estado) {
        return repository.findById(id)
            .flatMap(entity -> {
                entity.setState(estado);
                return repository.save(entity);
            })
            .then();
    }
}