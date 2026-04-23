package com.example.fileprocessor.infrastructure.r2dbc.adapter;

import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcSoapCommunicationLogRepository implements SoapCommunicationLogRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcSoapCommunicationLogRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcSoapCommunicationLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<SoapCommunicationLog> save(SoapCommunicationLog entry) {
        String sql = """
            INSERT INTO soap_communication_log (trace_id, status, retry_count, error_code, filename, parent_document_id, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7)
            """;

        return databaseClient.sql(sql)
            .bind("$1", entry.getTraceId())
            .bind("$2", entry.getStatus())
            .bind("$3", entry.getRetryCount())
            .bind("$4", entry.getErrorCode() != null ? entry.getErrorCode() : "")
            .bind("$5", entry.getFilename())
            .bind("$6", entry.getParentDocumentId() != null ? entry.getParentDocumentId() : "")
            .bind("$7", entry.getCreatedAt())
            .fetch()
            .first()
            .thenReturn(entry)
            .doOnSuccess(saved -> log.info("Logged SOAP communication: traceId={}, status={}, retries={}",
                saved.getTraceId(), saved.getStatus(), saved.getRetryCount()));
    }

    @Override
    public Flux<SoapCommunicationLog> findByParentDocumentId(String parentDocumentId) {
        String sql = """
            SELECT trace_id, status, retry_count, error_code, filename, parent_document_id, created_at
            FROM soap_communication_log
            WHERE parent_document_id = $1
            ORDER BY created_at ASC
            """;

        return databaseClient.sql(sql)
            .bind("$1", parentDocumentId)
            .map((row, metadata) -> SoapCommunicationLog.builder()
                .traceId(row.get("trace_id", String.class))
                .status(row.get("status", String.class))
                .retryCount(row.get("retry_count", Integer.class))
                .errorCode(row.get("error_code", String.class))
                .filename(row.get("filename", String.class))
                .parentDocumentId(row.get("parent_document_id", String.class))
                .createdAt(row.get("created_at", java.time.Instant.class))
                .build())
            .all();
    }

    @Override
    public Mono<Boolean> existsByParentDocumentIdAndStatus(String parentDocumentId, String status) {
        String sql = """
            SELECT COUNT(*) FROM soap_communication_log
            WHERE parent_document_id = $1 AND status = $2
            """;

        return databaseClient.sql(sql)
            .bind("$1", parentDocumentId)
            .bind("$2", status)
            .map((row, metadata) -> row.get("count", Long.class) > 0)
            .first()
            .defaultIfEmpty(false);
    }
}