package com.example.fileprocessor.infrastructure.r2dbc.adapter;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class R2dbcProductDocumentRepository implements ProductDocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(R2dbcProductDocumentRepository.class);

    private final DatabaseClient databaseClient;

    public R2dbcProductDocumentRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<ProductDocumentToProcess> findPendingDocuments() {
        String sql = """
            SELECT document_id, product_id, parent_document_id, filename, content, content_type, origin, status, created_at, processed_at,
                   trace_id, soap_correlation_id, error_code
            FROM product_documents_to_process
            WHERE status IN ('%s', '%s', '%s')
            ORDER BY created_at ASC
            """.formatted(DocumentStatus.PENDING_VALUE, DocumentStatus.RETRY_VALUE, DocumentStatus.PROCESSING_VALUE);

        return databaseClient.sql(sql)
            .map((row, metadata) -> ProductDocumentToProcess.builder()
                .documentId(row.get("document_id", String.class))
                .productId(row.get("product_id", String.class))
                .parentDocumentId(row.get("parent_document_id", String.class))
                .filename(row.get("filename", String.class))
                .content(decodeContent(row.get("content", String.class)))
                .contentType(row.get("content_type", String.class))
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

    private byte[] decodeContent(String base64Content) {
        if (base64Content == null || base64Content.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(base64Content);
    }

    private String encodeContent(byte[] content) {
        if (content == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(content);
    }

    @Override
    public Flux<ProductDocumentToProcess> findPendingDocumentsByProduct(String productId) {
        String sql = """
            SELECT document_id, product_id, parent_document_id, filename, content, content_type, origin, status, created_at, processed_at,
                   trace_id, soap_correlation_id, error_code
            FROM product_documents_to_process
            WHERE product_id = $1 AND status IN ('%s', '%s', '%s')
            ORDER BY created_at ASC
            """.formatted(DocumentStatus.PENDING_VALUE, DocumentStatus.RETRY_VALUE, DocumentStatus.PROCESSING_VALUE);

        return databaseClient.sql(sql)
            .bind("$1", productId)
            .map((row, metadata) -> ProductDocumentToProcess.builder()
                .documentId(row.get("document_id", String.class))
                .productId(row.get("product_id", String.class))
                .parentDocumentId(row.get("parent_document_id", String.class))
                .filename(row.get("filename", String.class))
                .content(decodeContent(row.get("content", String.class)))
                .contentType(row.get("content_type", String.class))
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
            UPDATE product_documents_to_process
            SET status = '%s', trace_id = $2, processed_at = $3
            WHERE document_id = $1 AND status = '%s'
            """.formatted(DocumentStatus.PROCESSING_VALUE, DocumentStatus.PENDING_VALUE);

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
    public Mono<Void> save(ProductDocumentToProcess document) {
        String sql = """
            INSERT INTO product_documents_to_process (document_id, product_id, parent_document_id, filename, content, content_type, origin, status,
                                                    created_at, processed_at, trace_id, soap_correlation_id, error_code)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
            """;

        return databaseClient.sql(sql)
            .bind("$1", document.getDocumentId())
            .bind("$2", document.getProductId())
            .bind("$3", document.getParentDocumentId() != null ? document.getParentDocumentId() : "")
            .bind("$4", document.getFilename())
            .bind("$5", encodeContent(document.getContent()))
            .bind("$6", document.getContentType() != null ? document.getContentType() : "")
            .bind("$7", document.getOrigin())
            .bind("$8", document.getStatus())
            .bind("$9", document.getCreatedAt() != null ? document.getCreatedAt() : Instant.now())
            .bind("$10", document.getProcessedAt() != null ? document.getProcessedAt() : Instant.now())
            .bind("$11", document.getTraceId() != null ? document.getTraceId() : "")
            .bind("$12", document.getSoapCorrelationId() != null ? document.getSoapCorrelationId() : "")
            .bind("$13", document.getErrorCode() != null ? document.getErrorCode() : "")
            .fetch()
            .first()
            .then()
            .doOnSuccess(v -> log.info("Saved product document to process: {}", document.getDocumentId()));
    }

    @Override
    public Mono<Void> saveAll(Flux<ProductDocumentToProcess> documents) {
        return documents
            .flatMap(this::save)
            .then();
    }

    @Override
    public Mono<Void> updateStatus(String documentId, String status, String traceId,
                                   String soapCorrelationId, String errorCode) {
        String sql = """
            UPDATE product_documents_to_process
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
            .doOnSuccess(v -> log.info("Updated product document {} status to {}", documentId, status));
    }
}
