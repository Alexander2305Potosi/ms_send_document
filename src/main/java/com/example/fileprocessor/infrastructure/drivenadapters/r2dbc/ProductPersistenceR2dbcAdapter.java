package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.port.out.ProductPersistenceGateway;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class ProductPersistenceR2dbcAdapter implements ProductPersistenceGateway {

    private final DatabaseClient databaseClient;

    public ProductPersistenceR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(Product product) {
        String sql = """
            INSERT INTO productos_pendientes
                (nombre_producto, nombre, fecha_carga, estado, mensaje_error, fecha_creacion, fecha_actualizacion)
            VALUES (:nombreProducto, :nombre, :fechaCarga, :estado, :mensajeError, :fechaCreacion, :fechaActualizacion)
            """;

        LocalDateTime now = LocalDateTime.now();

        return databaseClient.sql(sql)
            .bind("nombreProducto", product.productId())
            .bind("nombre", product.name())
            .bind("fechaCarga", product.loadDate())
            .bind("estado", product.state())
            .bind("mensajeError", product.messageError())
            .bind("fechaCreacion", now)
            .bind("fechaActualizacion", now)
            .then();
    }
}
