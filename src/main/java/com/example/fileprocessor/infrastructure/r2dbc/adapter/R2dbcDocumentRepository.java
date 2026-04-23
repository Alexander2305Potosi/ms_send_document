package com.example.fileprocessor.infrastructure.r2dbc.adapter;

import com.example.fileprocessor.domain.entity.DocumentToProcess;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
public class R2dbcDocumentRepository implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcDocumentRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcDocumentRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<DocumentToProcess> findPendingDocuments() {
        String sql = """
            SELECT document_id, filename, origin, status, created_at, processed_at, trace_id, soap_correlation_id, error_code
            FROM documents_to_process
            WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
            ORDER BY created_at ASC
            """;

        return databaseClient.sql(sql)
            .map((row, metadata) -> DocumentToProcess.builder()
                .documentId(row.get("document_id", String.class))
                .filename(row.get("filename", String.class))
                .origin(row.get("origin", String.class))
                .status(row.get("status", String.class))
                .createdAt(row.get("created_at", Instant.class))
                .processedAt(row.get("processed_at", Instant.class))
                .traceId(row.get("trace_id", String.class))
                .soapCorrelationId(row.get("soap_correlation_id", String.class))
                .errorCode(row.get("error_code", String.class))
                .build())
            .all();
    }

    @Override
    public Mono<Boolean> claimDocument(String documentId) {
        String sql = """
            UPDATE documents_to_process
            SET status = 'PROCESSING', trace_id = $2, processed_at = $3
            WHERE document_id = $1 AND status = 'PENDING'
            """;

        String traceId = UUID.randomUUID().toString();

        return databaseClient.sql(sql)
            .bind("$1", documentId)
            .bind("$2", traceId)
            .bind("$3", Instant.now())
            .fetch()
            .rowsUpdated()
            .map(rowsUpdated -> rowsUpdated > 0)
            .doOnSuccess(claimed -> log.info("Claim document {}: {}", documentId, claimed));
    }

    @Override
    public Mono<Void> updateStatus(String documentId, String status, String traceId, String soapCorrelationId, String errorCode) {
        String sql = """
            UPDATE documents_to_process
            SET status = $2, trace_id = $3, soap_correlation_id = $4, error_code = $5, processed_at = $6
            WHERE document_id = $1
            """;

        return databaseClient.sql(sql)
            .bind("$1", documentId)
            .bind("$2", status)
            .bind("$3", traceId != null ? traceId : "")
            .bind("$4", soapCorrelationId != null ? soapCorrelationId : "")
            .bind("$5", errorCode != null ? errorCode : "")
            .bind("$6", Instant.now())
            .fetch()
            .first()
            .then()
            .doOnSuccess(v -> log.info("Updated document {} status to {}", documentId, status));
    }
}
