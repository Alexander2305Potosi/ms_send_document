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
@Table(name = "productos")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
}