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

@Table("historico_documentos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentHistoryEntity {

    @Id
    @Column("id_historico_documentos")
    private Long id;

    @Column("id_documento")
    private String documentId;

    @Column("id_producto")
    private String productId;

    @Column("activo")
    private Boolean active;

    @Column("clave_documento")
    private String docKey;

    @Column("nombre")
    private String name;

    @Column("propietario")
    private String owner;

    @Column("ruta")
    private String path;

    @Column("estado")
    private String state;

    @Column("version_contrato")
    private String versionContract;

    @Column("mensaje_error")
    private String errorMessage;

    @Column("es_zip")
    private Boolean isZip;

    @Column("nombre_zip_padre")
    private String parentZipName;

    @Column("caso_uso")
    private String useCase;

    @Column("codigo_error")
    private String errorCode;

    @Column("reintentos")
    private Integer retry;

    @Column("operacion")
    private String operation;

    @Column("message_id")
    private String messageId;

    @Column("stack_trace")
    private String stackTrace;

    @Column("fecha_inicio")
    private LocalDateTime startedAt;

    @Column("fecha_fin")
    private LocalDateTime completedAt;

    @Column("fecha_creacion")
    private LocalDateTime createdAt;

    @Column("fecha_actualizacion")
    private LocalDateTime updatedAt;
}