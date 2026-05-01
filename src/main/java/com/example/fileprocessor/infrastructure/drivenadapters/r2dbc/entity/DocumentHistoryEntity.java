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

    @Column(name = "id_producto", nullable = false)
    private String productId;

    @Column(name = "id_documento", nullable = false)
    private String documentId;

    @Column(name = "nombre_archivo", nullable = false)
    private String filename;

    @Column(name = "nombre_comprimido")
    private String compressedFilename;

    @Column(name = "estado", nullable = false)
    private String status;

    @Column(name = "codigo_error")
    private String errorCode;

    @Column(name = "razon_fallo")
    private String failureReason;

    @Column(name = "numero_intentos", nullable = false)
    private int attemptCount;

    @Column(name = "fecha_envio")
    private LocalDateTime sentAt;

    @Column(name = "fecha_fallo")
    private LocalDateTime failedAt;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;
}