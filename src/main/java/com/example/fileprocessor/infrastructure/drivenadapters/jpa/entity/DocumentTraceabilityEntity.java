package com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "historico_documentos")
public class DocumentTraceabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_producto", nullable = false)
    private String productId;

    @Column(name = "nombre_documento", nullable = false)
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
    private int attemptCount = 1;

    @Column(name = "fecha_envio")
    private LocalDateTime sentAt;

    @Column(name = "fecha_fallo")
    private LocalDateTime failedAt;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;

    public DocumentTraceabilityEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getCompressedFilename() {
        return compressedFilename;
    }

    public void setCompressedFilename(String compressedFilename) {
        this.compressedFilename = compressedFilename;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
