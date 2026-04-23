package com.example.fileprocessor.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DatabaseClient databaseClient;

    public DatabaseInitializer(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize()
            .doOnSuccess(v -> log.info("Database ready"))
            .doOnError(e -> log.error("Database initialization failed: {}", e.getMessage()))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
            .onErrorResume(e -> Mono.empty())
            .subscribe();
    }

    public Mono<Void> initialize() {
        log.info("Initializing database schema...");

        return resetProcessingDocuments()
            .then(createDocumentsToProcessTable())
            .then(createSoapCommunicationLogTable())
            .then(createIndexes());
    }

    private Mono<Void> resetProcessingDocuments() {
        return databaseClient.sql("UPDATE documents_to_process SET status = 'PENDING' WHERE status = 'PROCESSING'")
            .fetch()
            .rowsUpdated()
            .doOnNext(count -> {
                if (count > 0) {
                    log.info("Crash recovery: reset {} PROCESSING documents to PENDING", count);
                } else {
                    log.info("No PROCESSING documents to reset");
                }
            })
            .then()
            .onErrorResume(e -> {
                log.debug("Crash recovery skipped (table may not exist yet): {}", e.getMessage());
                return Mono.empty();
            });
    }

    private Mono<Void> createDocumentsToProcessTable() {
        log.info("Creating documents_to_process table...");
        return databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS documents_to_process (
                    document_id VARCHAR(255) PRIMARY KEY,
                    filename VARCHAR(255),
                    origin VARCHAR(500),
                    status VARCHAR(50) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    processed_at TIMESTAMP,
                    trace_id VARCHAR(255),
                    soap_correlation_id VARCHAR(255),
                    error_code VARCHAR(100)
                )
                """)
            .fetch()
            .first()
            .then();
    }

    private Mono<Void> createSoapCommunicationLogTable() {
        log.info("Creating soap_communication_log table...");
        return databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS soap_communication_log (
                    trace_id VARCHAR(255) PRIMARY KEY,
                    status VARCHAR(50) NOT NULL,
                    retry_count INT NOT NULL,
                    error_code VARCHAR(100),
                    filename VARCHAR(255),
                    created_at TIMESTAMP NOT NULL
                )
                """)
            .fetch()
            .first()
            .then();
    }

    private Mono<Void> createIndexes() {
        log.info("Creating indexes...");
        return databaseClient.sql("CREATE INDEX IF NOT EXISTS idx_documents_status ON documents_to_process(status)")
            .fetch()
            .first()
            .then();
    }
}
