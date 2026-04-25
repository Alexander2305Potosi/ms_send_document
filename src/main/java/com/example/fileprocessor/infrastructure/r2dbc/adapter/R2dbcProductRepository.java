package com.example.fileprocessor.infrastructure.r2dbc.adapter;

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
            SELECT product_id, name, status, created_at, processed_at, trace_id
            FROM products_to_process
            WHERE status IN ('%s', '%s', '%s')
            ORDER BY created_at ASC
            """.formatted(DocumentStatus.PENDING_VALUE, DocumentStatus.RETRY_VALUE, DocumentStatus.PROCESSING_VALUE);

        return databaseClient.sql(sql)
            .map((row, metadata) -> ProductToProcess.builder()
                .productId(row.get("product_id", String.class))
                .name(row.get("name", String.class))
                .status(row.get("status", String.class))
                .createdAt(row.get("created_at", Instant.class))
                .processedAt(row.get("processed_at", Instant.class))
                .traceId(row.get("trace_id", String.class))
                .build())
            .all();
    }

    @Override
    public Mono<Void> save(ProductToProcess product) {
        String sql = """
            INSERT INTO products_to_process (product_id, name, status, created_at, processed_at, trace_id)
            VALUES ($1, $2, $3, $4, $5, $6)
            """;

        return databaseClient.sql(sql)
            .bind("$1", product.getProductId())
            .bind("$2", product.getName())
            .bind("$3", product.getStatus())
            .bind("$4", product.getCreatedAt() != null ? product.getCreatedAt() : Instant.now())
            .bind("$5", product.getProcessedAt() != null ? product.getProcessedAt() : Instant.now())
            .bind("$6", product.getTraceId() != null ? product.getTraceId() : "")
            .fetch()
            .first()
            .then()
            .doOnSuccess(v -> log.info("Saved product to process: {}", product.getProductId()));
    }

    @Override
    public Mono<Void> updateStatus(String productId, String status, String traceId) {
        String sql = """
            UPDATE products_to_process
            SET status = $2, trace_id = $3, processed_at = $4
            WHERE product_id = $1
            """;

        return databaseClient.sql(sql)
            .bind("$1", productId)
            .bind("$2", status)
            .bind("$3", traceId != null ? traceId : "")
            .bind("$4", Instant.now())
            .fetch()
            .first()
            .then()
            .doOnSuccess(v -> log.info("Updated product {} status to {}", productId, status));
    }
}
