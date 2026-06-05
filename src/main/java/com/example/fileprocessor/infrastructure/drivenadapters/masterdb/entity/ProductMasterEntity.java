package com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("productos_maestros")
public class ProductMasterEntity {
    @Id
    private Long id;
    
    @Column("id_producto")
    private String productId;
    
    private String nombre;
    
    @Column("fecha_cargue")
    private LocalDateTime fechaCargue;
    
    private String estado;
    
    @Column("carpeta_origen")
    private String carpetaOrigen;
    
    @Column("pais_origen")
    private String paisOrigen;
}
