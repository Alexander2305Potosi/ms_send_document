package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.*;

@Table("schemAnimals.animals_maestro")
@Getter
// Sin @Setter — entidad de solo lectura (fuente de datos maestra)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnimalMaestroEntity {
    @Id
    private Long id;

    @Column("name")
    private String name;

    @Column("category")
    private String category;
}
