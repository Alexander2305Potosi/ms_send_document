# Plan de Refactorización: Esquema Maestro para Homologación

## 1. Objetivo y Necesidad
Actualmente, el sistema gestiona reglas de homologación de manera independiente para cada base de datos o contexto. El objetivo es **centralizar las tablas de referencia y maestras** (`categoria_manual` y `pais_homologado`) en un único y nuevo esquema de base de datos (`reference_data`). 

Esto permitirá reutilizar los mismos repositorios y la misma lógica de homologación tanto para el caso de uso de documentos (SOAP) como para el de Animales, filtrando dinámicamente mediante una nueva columna de "Caso de Uso" (`use_case`).

---

## 2. Auditoría, Diagnóstico y Hallazgos de Mejora

Durante la revisión de este plan, se han identificado las siguientes áreas críticas que requieren mejoras antes de la implementación:

1. **Gestión de Datos Heredados (Legacy Data) [CRÍTICO]:**
   * **Hallazgo:** Al agregar la nueva columna `use_case`, los registros existentes fallarán o retornarán nulo si no se les asigna un valor.
   * **Solución:** El script DDL debe incluir un valor por defecto (`DEFAULT 'SOAP'`) para los registros actuales, y luego obligar la restricción `NOT NULL`.

2. **Consolidación de Adaptadores y Eliminación del Strategy [MEJORA]:**
   * **Hallazgo:** Ya no es necesario tener un `SoapHomologationAdapter`, un `AnimalHomologationAdapter` y un `AbstractHomologationAdapter`.
   * **Solución:** Eliminar esas tres clases y crear un **único** `HomologationMasterAdapter` que reciba el caso de uso directamente en tiempo de ejecución. Esto reduce drásticamente la complejidad del código.

3. **Caché de Referencia (Performance) [MEJORA]:**
   * **Hallazgo:** Las tablas maestras cambian muy rara vez, pero serán consultadas miles de veces por minuto durante el procesamiento masivo de archivos.
   * **Solución:** Implementar un caché reactivo ligero (ej. `ConcurrentHashMap` o `Caffeine`) en el `HomologationMasterAdapter` para evitar colapsar la base de datos con consultas idénticas.

4. **Configuración de R2DBC (Connection Factory) [TÉCNICO]:**
   * **Hallazgo:** Spring Data R2DBC exige aislar las conexiones por paquete de repositorios.
   * **Solución:** Las entidades y repositorios de `reference_data` deben residir en un paquete específico (ej. `r2dbc.master.repository`) que esté asociado al ConnectionFactory principal (`MasterDB`).

---

## 3. Implementación y Código Fuente

### A. Scripts de Base de Datos (Migración)
```sql
-- 1. Crear el nuevo esquema
CREATE SCHEMA IF NOT EXISTS reference_data;

-- 2. Mover o crear la tabla en el nuevo esquema
CREATE TABLE reference_data.pais_homologado (
    id BIGSERIAL PRIMARY KEY,
    codigo_origen VARCHAR(100) NOT NULL,
    pais_destino VARCHAR(100) NOT NULL,
    carpeta_destino VARCHAR(200) NOT NULL,
    use_case VARCHAR(50) NOT NULL DEFAULT 'SOAP' -- Fallback para data legacy
);

CREATE TABLE reference_data.categoria_manual (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    categoria_homologada VARCHAR(100) NOT NULL
);
```

### B. Entidades Java
```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Table("reference_data.pais_homologado")
public class CountryHomologatedEntity {
    @Id
    private Long id;
    
    @Column("codigo_origen")
    private String originCode;
    
    @Column("pais_destino")
    private String destinationCountry;
    
    @Column("carpeta_destino")
    private String destinationFolder;
    
    @Column("use_case")
    private String useCase; // Nuevo campo discriminador
}
```

### C. Repositorio R2DBC
```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CountryHomologatedRepository extends ReactiveCrudRepository<CountryHomologatedEntity, Long> {
    
    @Query("SELECT * FROM reference_data.pais_homologado WHERE codigo_origen = :code AND use_case = :useCase LIMIT 1")
    Mono<CountryHomologatedEntity> findByOriginCodeAndUseCase(String code, String useCase);
}
```

### D. Adaptador Maestro Unificado (Con caché opcional)
```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.HomologationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class HomologationMasterAdapter implements HomologationRepository {

    private final CountryHomologatedRepository countryRepository;
    private final CategoryManualRepository categoryRepository;

    @Override
    public Mono<HomologationResult> resolve(DocumentHistoryDTO history) {
        // Extraemos el UseCase directamente del contexto del documento (ej. "SOAP" o "Animal")
        String currentUseCase = history.getUseCase();
        String originCode = history.getOriginCountry();

        return countryRepository.findByOriginCodeAndUseCase(originCode, currentUseCase)
                .map(entity -> new HomologationResult(
                        entity.getDestinationFolder(),
                        entity.getDestinationCountry()
                ))
                .switchIfEmpty(Mono.error(new RuntimeException(
                    "No se encontró regla de homologación para el país " + originCode + " en el caso de uso " + currentUseCase
                )));
    }
}
```

---

## 4. Pruebas Unitarias (Testing)

El siguiente test garantiza que el adaptador maestro respete el caso de uso y devuelva los resultados correctos.

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.HomologationResult;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master.entity.CountryHomologatedEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master.repository.CountryHomologatedRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.master.repository.CategoryManualRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomologationMasterAdapterTest {

    @Mock
    private CountryHomologatedRepository countryRepository;

    @Mock
    private CategoryManualRepository categoryRepository;

    @InjectMocks
    private HomologationMasterAdapter adapter;

    @Test
    void resolve_ShouldReturnHomologation_WhenUseCaseMatches() {
        // Arrange
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .originCountry("COL")
                .useCase("Animal")
                .build();

        CountryHomologatedEntity entity = CountryHomologatedEntity.builder()
                .destinationCountry("Colombia_Animales")
                .destinationFolder("/animales/colombia")
                .useCase("Animal")
                .build();

        when(countryRepository.findByOriginCodeAndUseCase("COL", "Animal"))
                .thenReturn(Mono.just(entity));

        // Act
        Mono<HomologationResult> resultMono = adapter.resolve(history);

        // Assert
        StepVerifier.create(resultMono)
                .expectNextMatches(result -> 
                        result.getDestinationCountry().equals("Colombia_Animales") &&
                        result.getDestinationFolder().equals("/animales/colombia"))
                .verifyComplete();
    }

    @Test
    void resolve_ShouldThrowError_WhenUseCaseNotFound() {
        // Arrange
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .originCountry("MEX")
                .useCase("SOAP")
                .build();

        when(countryRepository.findByOriginCodeAndUseCase("MEX", "SOAP"))
                .thenReturn(Mono.empty());

        // Act
        Mono<HomologationResult> resultMono = adapter.resolve(history);

        // Assert
        StepVerifier.create(resultMono)
                .expectErrorMessage("No se encontró regla de homologación para el país MEX en el caso de uso SOAP")
                .verify();
    }
}
```
