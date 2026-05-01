package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentTraceability;
import com.example.fileprocessor.domain.port.out.DocumentTraceabilityGateway;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class DocumentTraceabilityR2dbcAdapter implements DocumentTraceabilityGateway {

    private final DatabaseClient databaseClient;
    private final DocumentTraceabilityRowMapper rowMapper;

    public DocumentTraceabilityR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.rowMapper = new DocumentTraceabilityRowMapper();
    }

    @Override
    public Mono<Void> save(DocumentTraceability record) {
        String sql = """
            INSERT INTO historico_documentos
                (nombre_producto, nombre_documento, nombre_archivo, nombre_comprimido,
                 estado, codigo_error, razon_fallo, numero_intentos, fecha_envio, fecha_fallo, fecha_creacion)
            VALUES (:nombreProducto, :nombreDocumento, :nombreArchivo, :nombreComprimido,
                    :estado, :codigoError, :razonFallo, :numeroIntentos, :fechaEnvio, :fechaFallo, :fechaCreacion)
            """;

        return databaseClient.sql(sql)
            .bind("nombreProducto", record.productId())
            .bind("nombreDocumento", record.documentId())
            .bind("nombreArchivo", record.filename())
            .bind("nombreComprimido", record.compressedFilename())
            .bind("estado", record.status())
            .bind("codigoError", record.errorCode())
            .bind("razonFallo", record.failureReason())
            .bind("numeroIntentos", record.attemptCount())
            .bind("fechaEnvio", record.sentAt())
            .bind("fechaFallo", record.failedAt())
            .bind("fechaCreacion", record.createdAt())
            .then();
    }

    @Override
    public Flux<DocumentTraceability> findByProductId(String productId) {
        String sql = "SELECT * FROM historico_documentos WHERE nombre_producto = :productId";

        return databaseClient.sql(sql)
            .bind("productId", productId)
            .map(rowMapper)
            .all();
    }

    @Override
    public Flux<DocumentTraceability> findByStatus(String status) {
        String sql = "SELECT * FROM historico_documentos WHERE estado = :status";

        return databaseClient.sql(sql)
            .bind("status", status)
            .map(rowMapper)
            .all();
    }
}
