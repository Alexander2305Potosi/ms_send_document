package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("categoria_manual")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryManualEntity {

    @Id
    private Long id;

    @Column("categoria")
    private String categoria;

    @Column("descripcion_manual")
    private String descripcionManual;

    @Column("fecha_vigencia")
    private LocalDate fechaVigencia;

    @Column("fecha_creacion")
    private LocalDateTime createdAt;
}