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

    @Column("nombre_documento")
    private String name;

    @Column("estado_sincronizacion")
    private String state;

    @Column("mensaje_sincronizacion")
    private String syncMessage;

    @Column("es_zip")
    private Boolean isZip;

    @Column("caso_uso")
    private String useCase;

    @Column("carpeta_origen")
    private String originFolder;

    @Column("pais_origen")
    private String originCountry;

    @Column("carpeta_homologada")
    private String homologationFolder;

    @Column("pais_homologado")
    private String homologationCountry;

    @Column("categoria_homologado")
    private String categoriaHomologada;

    @Column("sucursal")
    private String sucursal;

    @Column("reintentos")
    private Integer retryCount;

    @Column("fecha_carga")
    private LocalDateTime createdAt;

    @Column("fecha_carga_actualizacion")
    private LocalDateTime updatedAt;
}
