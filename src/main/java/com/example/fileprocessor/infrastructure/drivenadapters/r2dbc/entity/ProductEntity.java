package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
    @Column(name = "id_producto")
    private String productId;

    @Column(name = "nombre", nullable = false)
    private String name;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime loadDate;

    @Column(name = "estado", nullable = false)
    private String state;

    @Column(name = "mensaje_error")
    private String messageError;

    public ProductEntity() {}

    public ProductEntity(String productId, String name, LocalDateTime loadDate, String state, String messageError) {
        this.productId = productId;
        this.name = name;
        this.loadDate = loadDate;
        this.state = state;
        this.messageError = messageError;
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
}