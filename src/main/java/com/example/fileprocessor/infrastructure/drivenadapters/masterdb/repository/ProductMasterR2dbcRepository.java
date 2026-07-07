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

    // MODIFICADO: Añadido filtro por lastProductId ($4) y ordenamiento determinista con casts para compatibilidad con H2
    @Query("SELECT * FROM productos_maestros WHERE (CAST($1 AS VARCHAR) IS NULL OR estado = $1) AND (CAST($2 AS TIMESTAMP) IS NULL OR CAST($3 AS TIMESTAMP) IS NULL OR fecha_cargue BETWEEN $2 AND $3) AND (CAST($4 AS VARCHAR) IS NULL OR id_producto > $4) ORDER BY id_producto ASC")
    Flux<ProductMasterEntity> findAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd, String lastProductId);

    // MODIFICADO: Añadido filtro por lastProductId ($4) con casts para compatibilidad con H2
    @Query("SELECT COUNT(*) FROM productos_maestros WHERE (CAST($1 AS VARCHAR) IS NULL OR estado = $1) AND (CAST($2 AS TIMESTAMP) IS NULL OR CAST($3 AS TIMESTAMP) IS NULL OR fecha_cargue BETWEEN $2 AND $3) AND (CAST($4 AS VARCHAR) IS NULL OR id_producto > $4)")
    Mono<Long> countAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd, String lastProductId);
}
