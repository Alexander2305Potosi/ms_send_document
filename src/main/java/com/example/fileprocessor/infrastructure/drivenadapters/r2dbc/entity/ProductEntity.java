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

@Table("productos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductEntity {

    @Id
    private Long id;

    @Column("id_producto")
    private String productId;

    @Column("nombre")
    private String name;

    @Column("fecha_carga")
    private LocalDateTime loadDate;

    @Column("estado")
    private String state;

    @Column("mensaje_error")
    private String messageError;
}