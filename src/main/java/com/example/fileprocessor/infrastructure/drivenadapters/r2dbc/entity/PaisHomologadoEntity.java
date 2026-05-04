package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pais_homologado")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaisHomologadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pais", nullable = false)
    private String pais;

    @Column(name = "pais_homologado", nullable = false)
    private String paisHomologado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;
}