package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * R2DBC entity for the 'historico_documentos' table.
 * Removed: message_id (no external broker defined), fecha_creacion (redundant with fecha_inicio).
 */
@Table("historico_documentos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentHistoryEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("documento_id")
    private Long documentId;

    @Column("nombre_archivo")
    private String filename;

    @Column("operacion")
    private String operation;

    @Column("resultado")
    private String result;

    @Column("codigo_error")
    private String errorCode;

    @Column("mensaje_error")
    private String errorMessage;

    @Column("stack_trace")
    private String stackTrace;

    @Column("reintentos")
    private Integer retry;

    @Column("fecha_inicio")
    private LocalDateTime startedAt;

    @Column("fecha_fin")
    private LocalDateTime completedAt;
}
