package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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

    @Column("prefijo")
    private String prefijo;

    @Column("categoria_homologado")
    private String categoriaHomologado;

}
