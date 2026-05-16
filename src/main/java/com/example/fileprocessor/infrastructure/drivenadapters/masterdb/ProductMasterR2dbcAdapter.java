package com.example.fileprocessor.infrastructure.drivenadapters.masterdb;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ProductMasterR2dbcAdapter implements ProductMasterRepository {

    private static final Logger LOGGER = Logger.getLogger(ProductMasterR2dbcAdapter.class.getName());

    @Qualifier("masterDatabaseClient")
    private final DatabaseClient masterDatabaseClient;

    @Override
    public Flux<ProductMaestro> getAllProducts() {
        LOGGER.info("Fetching master products from EXTERNAL DATABASE");
        return masterDatabaseClient.sql("SELECT id, id_producto, nombre, fecha_cargue, estado, carpeta_origen, pais_origen FROM productos_maestros")
            .map((row, metadata) -> ProductMaestro.builder()
                .id(row.get("id", Long.class))
                .productId(row.get("id_producto", String.class))
                .name(row.get("nombre", String.class))
                .loadDate(row.get("fecha_cargue", java.time.LocalDateTime.class))
                .state(row.get("estado", String.class))
                .originFolder(row.get("carpeta_origen", String.class))
                .originCountry(row.get("pais_origen", String.class))
                .build())
            .all();
    }
}
