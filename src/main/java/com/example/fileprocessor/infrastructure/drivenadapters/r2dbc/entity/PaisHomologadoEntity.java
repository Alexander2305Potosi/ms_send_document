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

    @Column("carpeta_origen")
    private String originFolder;

    @Column("pais_origen")
    private String originCountry;

    @Column("carpeta_homologada")
    private String homologationFolder;

    @Column("pais_homologado")
    private String homologationCountry;

}
