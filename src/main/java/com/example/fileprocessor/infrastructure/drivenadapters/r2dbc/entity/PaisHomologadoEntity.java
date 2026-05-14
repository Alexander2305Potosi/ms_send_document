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

@Table("pais_homologado")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaisHomologadoEntity {

    @Id
    private Long id;

    @Column("pais")
    private String pais;

    @Column("pais_homologado")
    private String paisHomologado;

    @Column("fecha_creacion")
    private LocalDateTime createdAt;
}
