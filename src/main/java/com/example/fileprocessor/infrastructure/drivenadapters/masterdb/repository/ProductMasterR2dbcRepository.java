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

    // MODIFICADO: Añadido filtro por lastProductId ($4) y ordenamiento determinista
    @Query("SELECT * FROM productos_maestros WHERE ($1 IS NULL OR estado = $1) AND ($2 IS NULL OR $3 IS NULL OR fecha_cargue BETWEEN $2 AND $3) AND ($4 IS NULL OR id_producto > $4) ORDER BY id_producto ASC")
    Flux<ProductMasterEntity> findAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd, String lastProductId);

    // MODIFICADO: Añadido filtro por lastProductId ($4)
    @Query("SELECT COUNT(*) FROM productos_maestros WHERE ($1 IS NULL OR estado = $1) AND ($2 IS NULL OR $3 IS NULL OR fecha_cargue BETWEEN $2 AND $3) AND ($4 IS NULL OR id_producto > $4)")
    Mono<Long> countAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd, String lastProductId);
}
