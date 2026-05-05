package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "historico_documentos")
public class DocumentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_documento", nullable = false)
    private String documentId;

    @Column(name = "id_producto", nullable = false)
    private String productId;

    @Builder.Default
    @Column(name = "activo")
    private Boolean active = true;

    @Column(name = "clave_documento")
    private String docKey;

    @Column(name = "nombre")
    private String name;

    @Column(name = "propietario")
    private String owner;

    @Column(name = "ruta")
    private String path;

    @Column(name = "estado", nullable = false)
    private String state;

    @Column(name = "version_contrato")
    private String versionContract;

    @Column(name = "mensaje_error")
    private String errorMessage;

    @Builder.Default
    @Column(name = "es_zip")
    private Boolean isZip = false;

    @Column(name = "nombre_zip_padre")
    private String parentZipName;

    @Column(name = "caso_uso")
    private String useCase;

    @Column(name = "resultado")
    private String status;

    @Column(name = "codigo_error")
    private String errorCode;

    @Builder.Default
    @Column(name = "reintentos")
    private Integer retry = 0;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime updatedAt;
}
