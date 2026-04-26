package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class R2dbcSoapCommunicationLogRepository implements SoapCommunicationLogRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcSoapCommunicationLogRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcSoapCommunicationLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<SoapCommunicationLog> save(SoapCommunicationLog entry) {
        String sql = """
            INSERT INTO soap_communication_log (trace_id, document_id, status, retry_count, error_code, filename, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7)
            """;

        return databaseClient.sql(sql)
            .bind("$1", entry.getTraceId())
            .bind("$2", entry.getDocumentId() != null ? entry.getDocumentId() : "")
            .bind("$3", entry.getStatus())
            .bind("$4", entry.getRetryCount())
            .bind("$5", entry.getErrorCode() != null ? entry.getErrorCode() : "")
            .bind("$6", entry.getFilename())
            .bind("$7", entry.getCreatedAt())
            .fetch()
            .first()
            .thenReturn(entry)
            .doOnSuccess(saved -> log.info("Logged SOAP communication: traceId={}, documentId={}, status={}, retries={}",
                saved.getTraceId(), saved.getDocumentId(), saved.getStatus(), saved.getRetryCount()));
    }
}
