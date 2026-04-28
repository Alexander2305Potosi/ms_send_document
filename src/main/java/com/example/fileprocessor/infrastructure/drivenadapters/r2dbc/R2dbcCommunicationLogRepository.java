package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.CommunicationLog;
import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class R2dbcCommunicationLogRepository implements CommunicationLogRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcCommunicationLogRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcCommunicationLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<CommunicationLog> save(CommunicationLog entry) {
        String sql = """
            INSERT INTO communication_log (trace_id, document_id, status, retry_count, error_code, filename, created_at, latency_ms, gateway_name, metadata)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            """;

        return databaseClient.sql(sql)
            .bind("$1", entry.getTraceId())
            .bind("$2", entry.getDocumentId() != null ? entry.getDocumentId() : "")
            .bind("$3", entry.getStatus())
            .bind("$4", entry.getRetryCount())
            .bind("$5", entry.getErrorCode() != null ? entry.getErrorCode() : "")
            .bind("$6", entry.getFilename())
            .bind("$7", entry.getCreatedAt())
            .bind("$8", entry.getLatencyMs() != null ? entry.getLatencyMs() : 0L)
            .bind("$9", entry.getGatewayName() != null ? entry.getGatewayName() : "")
            .bind("$10", entry.getMetadata() != null ? entry.getMetadata() : "{}")
            .fetch()
            .first()
            .thenReturn(entry)
            .doOnSuccess(saved -> log.info("Logged communication: traceId={}, documentId={}, status={}, retries={}, latencyMs={}",
                saved.getTraceId(), saved.getDocumentId(), saved.getStatus(), saved.getRetryCount(), saved.getLatencyMs()));
    }
}
