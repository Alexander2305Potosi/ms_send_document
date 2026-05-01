package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.port.out.ProductDbGateway;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class ProductDbR2dbcAdapter implements ProductDbGateway {

    private final DatabaseClient databaseClient;
    private final PendingProductRowMapper rowMapper;

    public ProductDbR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.rowMapper = new PendingProductRowMapper();
    }

    @Override
    public Flux<Product> findByLoadDate(LocalDate loadDate) {
        LocalDateTime startOfDay = loadDate.atStartOfDay();
        LocalDateTime endOfDay = loadDate.atTime(LocalTime.MAX);

        String sql = """
            SELECT * FROM productos_pendientes
            WHERE fecha_carga >= :startOfDay
              AND fecha_carga <= :endOfDay
              AND estado = :estado
            """;

        return databaseClient.sql(sql)
            .bind("startOfDay", startOfDay)
            .bind("endOfDay", endOfDay)
            .bind("estado", "PENDING")
            .map(rowMapper)
            .all();
    }

    @Override
    public Flux<Product> findAll() {
        String sql = "SELECT * FROM productos_pendientes";

        return databaseClient.sql(sql)
            .map(rowMapper)
            .all();
    }
}
