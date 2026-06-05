package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

@Component
public class ProductLocalR2dbcAdapter implements ProductLocalRepository {

    private static final Logger LOGGER = Logger.getLogger(ProductLocalR2dbcAdapter.class.getName());

    private final DatabaseClient databaseClient;
    private final String query;

    public ProductLocalR2dbcAdapter(DatabaseClient databaseClient,
                                    @Value("${app.sync.sucursal-query}") String query) {
        this.databaseClient = databaseClient;
        this.query = query;
    }

    @Override
    public Mono<String> findBranchByProductId(String productId) {
        return databaseClient.sql(query)
                .bind("productId", productId)
                .map((row, metadata) -> row.get(0, String.class))
                .one()
                .onErrorResume(e -> {
                    LOGGER.warning("Error querying branch for product " + productId + ": " + e.getMessage());
                    return Mono.empty();
                });
    }
}
