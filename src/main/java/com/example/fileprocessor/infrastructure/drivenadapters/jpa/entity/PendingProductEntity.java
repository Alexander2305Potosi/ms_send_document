package com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos_pendientes")
public class PendingProductEntity {

    @Id
    @Column(name = "nombre_producto")
    private String productId;

    @Column(name = "nombre")
    private String name;

    @Column(name = "fecha_carga")
    private LocalDateTime loadDate;

    @Column(name = "estado")
    private String state;

    @Column(name = "mensaje_error")
    private String messageError;

    @Column(name = "fecha_creacion")
    private LocalDateTime createdAt;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime updatedAt;

    public PendingProductEntity() {}

    public PendingProductEntity(String productId, String name, LocalDateTime loadDate, String state) {
        this.productId = productId;
        this.name = name;
        this.loadDate = loadDate;
        this.state = state;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getLoadDate() { return loadDate; }
    public void setLoadDate(LocalDateTime loadDate) { this.loadDate = loadDate; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getMessageError() { return messageError; }
    public void setMessageError(String messageError) { this.messageError = messageError; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
