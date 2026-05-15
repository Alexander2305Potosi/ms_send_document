package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * R2DBC entity for the 'historico_documentos' table.
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

    @Column("id_documentos")
    private Long documentId;

    @Column("nombre_documento")
    private String filename;

    @Column("caso_uso")
    private String useCase;

    @Column("resultado")
    private String result;

    @Column("codigo_error")
    private String errorCode;

    @Column("mensaje_error")
    private String errorMessage;

    @Column("reintentos")
    private Integer retry;

    @Column("fecha_inicio_procesamiento")
    private Instant startedAt;

    @Column("fecha_fin_procesamiento")
    private Instant completedAt;
}
