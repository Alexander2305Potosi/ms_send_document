package com.example.fileprocessor.infrastructure.drivenadapters.masterdb.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity.ProductMasterEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@Repository
public interface ProductMasterR2dbcRepository extends R2dbcRepository<ProductMasterEntity, Long> {

    @Query("SELECT * FROM productos_maestros WHERE ($1 IS NULL OR estado = $1) AND ($2 IS NULL OR $3 IS NULL OR fecha_cargue BETWEEN $2 AND $3)")
    Flux<ProductMasterEntity> findAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd);

    @Query("SELECT COUNT(*) FROM productos_maestros WHERE ($1 IS NULL OR estado = $1) AND ($2 IS NULL OR $3 IS NULL OR fecha_cargue BETWEEN $2 AND $3)")
    Mono<Long> countAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd);
}
