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
 * R2DBC entity for the 'documentos' table.
 * Redundant columns removed: activo, propietario, ruta, fecha_actualizacion.
 */
@Table("documentos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("id_documento")
    private String documentId;

    @Column("id_producto")
    private String productId;

    @Column("nombre")
    private String name;

    @Column("estado")
    private String state;

    @Column("mensaje_error")
    private String errorMessage;

    @Column("es_zip")
    private Boolean isZip;

    @Column("caso_uso")
    private String useCase;

    @Column("reintentos")
    private Integer retryCount;

    @Column("fecha_creacion")
    private LocalDateTime createdAt;

    @Column("fecha_actualizacion")
    private LocalDateTime updatedAt;
}
