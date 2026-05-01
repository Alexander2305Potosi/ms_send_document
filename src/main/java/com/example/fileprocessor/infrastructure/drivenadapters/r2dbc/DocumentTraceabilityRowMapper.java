package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentTraceability;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.BeanPropertyRowMapper;

import java.time.LocalDateTime;

public class DocumentTraceabilityRowMapper extends BeanPropertyRowMapper<DocumentTraceability> {

    public DocumentTraceabilityRowMapper() {
        super(DocumentTraceability.class);
    }

    @Override
    public DocumentTraceability apply(Readable readable) {
        Row row = (Row) readable;
        return new DocumentTraceability(
            row.get("id", Long.class),
            row.get("nombre_producto", String.class),
            row.get("nombre_documento", String.class),
            row.get("nombre_archivo", String.class),
            row.get("nombre_comprimido", String.class),
            row.get("estado", String.class),
            row.get("codigo_error", String.class),
            row.get("razon_fallo", String.class),
            ((Number) row.get("numero_intentos", Integer.class)).intValue(),
            row.get("fecha_envio", LocalDateTime.class),
            row.get("fecha_fallo", LocalDateTime.class),
            row.get("fecha_creacion", LocalDateTime.class)
        );
    }
}
