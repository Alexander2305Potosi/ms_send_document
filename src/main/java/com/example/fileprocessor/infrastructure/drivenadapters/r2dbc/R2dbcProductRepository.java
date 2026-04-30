package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductToProcess;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class R2dbcProductRepository implements ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcProductRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcProductRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<ProductToProcess> findPendingProducts() {
        String sql = """
            SELECT product_id, name, status, created_at, processed_at
            FROM products_to_process
            WHERE status IN ($1, $2, $3)
            ORDER BY created_at ASC
            """;

        return databaseClient.sql(sql)
            .bind("$1", DocumentStatus.PENDING.name())
            .bind("$2", DocumentStatus.RETRY.name())
            .bind("$3", DocumentStatus.PROCESSING.name())
            .map((row, metadata) -> ProductToProcess.builder()
                .productId(row.get("product_id", String.class))
                .name(row.get("name", String.class))
                .status(row.get("status", String.class))
                .createdAt(row.get("created_at", Instant.class))
                .processedAt(row.get("processed_at", Instant.class))
                .build())
            .all();
    }

    @Override
    public Mono<Void> save(ProductToProcess product) {
        String sql = """
            INSERT INTO products_to_process (product_id, name, status, created_at, processed_at)
            VALUES ($1, $2, $3, $4, $5)
            """;

        return databaseClient.sql(sql)
            .bind("$1", product.getProductId())
            .bind("$2", product.getName())
            .bind("$3", product.getStatus())
            .bind("$4", product.getCreatedAt() != null ? product.getCreatedAt() : Instant.now())
            .bind("$5", product.getProcessedAt() != null ? product.getProcessedAt() : Instant.now())
            .fetch()
            .first()
            .then()
            .doOnSuccess(v -> log.info("Saved product to process: {}", product.getProductId()));
    }

    @Override
    public Mono<Void> updateStatus(String productId, String status) {
        String sql = """
            UPDATE products_to_process
            SET status = $2, processed_at = $3
            WHERE product_id = $1
            """;

        return databaseClient.sql(sql)
            .bind("$1", productId)
            .bind("$2", status)
            .bind("$3", Instant.now())
            .fetch()
            .first()
            .then()
            .doOnSuccess(v -> log.info("Updated product {} status to {}", productId, status));
    }
}
