package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.common.AbstractReactiveAdapterOperation;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.ProductEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.ProductMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class ProductR2dbcAdapter 
    extends AbstractReactiveAdapterOperation<ProductEntity, ProductHistory, Long, com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.ProductRepository> 
    implements ProductRepository {

    public ProductR2dbcAdapter(com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.ProductRepository repository) {
        super(repository, ProductMapper::toEntity, ProductMapper::toDomain);
    }

    @Override
    public Flux<ProductHistory> findByLoadDate(LocalDate loadDate) {
        LocalDateTime startOfDay = loadDate.atStartOfDay();
        LocalDateTime endOfDay = loadDate.atTime(LocalTime.MAX);
        return doQueryMany(() -> repository.findByLoadDateBetween(startOfDay, endOfDay));
    }

    // findAll() is inherited automatically!


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
