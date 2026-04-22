package com.example.fileprocessor.infrastructure.r2dbc.adapter;

import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

public class R2dbcSoapCommunicationLogRepository implements SoapCommunicationLogRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcSoapCommunicationLogRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcSoapCommunicationLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<SoapCommunicationLog> save(SoapCommunicationLog entry) {
        String sql = """
            INSERT INTO soap_communication_log (trace_id, status, retry_count, error_code, filename, created_at)
            VALUES ($1, $2, $3, $4, $5, $6)
            """;

        return databaseClient.sql(sql)
            .bind("$1", entry.traceId())
            .bind("$2", entry.status())
            .bind("$3", entry.retryCount())
            .bind("$4", entry.errorCode() != null ? entry.errorCode() : "")
            .bind("$5", entry.filename())
            .bind("$6", entry.createdAt())
            .fetch()
            .first()
            .thenReturn(entry)
            .doOnSuccess(saved -> log.info("Logged SOAP communication: traceId={}, status={}, retries={}",
                saved.traceId(), saved.status(), saved.retryCount()));
    }
}
