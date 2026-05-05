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

@Table("pais_homologado")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CountryHomologatedEntity {

    @Id
    private Long id;

    @Column("pais")
    private String country;

    @Column("pais_homologado")
    private String countryHomologated;

    @Column("fecha_creacion")
    private LocalDateTime createdAt;
}