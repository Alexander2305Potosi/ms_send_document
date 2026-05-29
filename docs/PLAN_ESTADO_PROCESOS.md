# Plan de Implementación Simplificado: Consulta de Estado de Procesos Asíncronos

Este plan describe la propuesta simplificada para consultar el estado de los procesos asíncronos consultando directamente la base de datos. La validación del tiempo límite **es responsabilidad del consumidor del endpoint**; los endpoints solo reportan el estado actual en BD.

---

## 1. Diseño de la Solución (Estados Únicos: "1" o "error consulta carga")

El cuerpo de la respuesta de ambos endpoints retornará únicamente un string directo:
* **En ejecución o completado con éxito:** `"1"`.
* **Error técnico o proceso incompleto:** `"error consulta carga"`.

> **Sin validación de tiempo límite:** el consumidor del endpoint decide si el proceso excedió el tiempo esperado.

---

### A. Sincronización (`GET /api/v1/products/sync/status/{type_job}`)

1. **Total Maestro:** `productMasterRepository.countAllProducts()`
2. **Total Local:** `documentRepository.countDocumentsCreatedToday(startOfDay, useCase)`

**Reglas de Decisión:**
* Si `localCount >= masterCount` → `"1"` (completado).
* Si `localCount < masterCount` → `"error consulta carga"` (sincronización incompleta).

---

### B. Procesamiento (`GET /api/v1/products/process/status/{type_job}`)

Consulta únicamente los totales agrupados por estado en la BD local de hoy, filtrados por `useCase`.

**Reglas de Decisión:**
* **Estado en progreso activo:** Únicamente `ON_PROCESSING` (si lleva menos de 1 hora de iniciado).
* **Estados excluidos:** `NO_SUCURSAL` y cualquier estado marcado como `ProcessingResultCodes.isBusinessRule(state)` (incluye `ERR_DUPLICATED_DOC`).
* **Regla de Expiración:** Si un documento en estado `ON_PROCESSING` tiene más de 1 hora desde su inicio (`fecha_carga`), se considera error técnico y el endpoint debe retornar `"error consulta carga"`.
* **Cálculo de Resultados:**
  * Si `totalApplicable == 0` → `"1"` (sin documentos que procesar).
  * Si `pending > 0` (documentos en `ON_PROCESSING` con menos de 1 hora) → `"1"` (procesamiento en curso).
  * Si `pending == 0 && technicalFailures > 0` (incluyendo stuck `ON_PROCESSING` con > 1 hora o estado `FAILED`) → `"error consulta carga"`.
  * Si `pending == 0 && technicalFailures == 0` → `"1"` (todo procesado correctamente).

---

## 2. Clases a Modificar y Crear

> **Nota sobre `ON_PROCESSING`**: La constante `ProcessingResultCodes.ON_PROCESSING` ya existe en la base de código actual, por lo que no es necesario modificar `ProcessingResultCodes.java` para declararla.


#### [MODIFY] [ApiConstants.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java)
```java
// MODIFICADO
package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

/**
 * API-level constants for REST endpoints and integrations.
 */
public final class ApiConstants {

    private ApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // HTTP headers
    public static final String HEADER_TRACE_ID = "message-id";
    public static final String HEADER_USE_CASE = "use-case";
    public static final String HEADER_DATE_INIT = "date_init";
    public static final String HEADER_DATE_END = "date_end";
    public static final String TYPE_JOB = "type_job";

    // Respuestas de estado del proceso
    // NUEVO
    public static final String STATUS_OK = "1";
    public static final String STATUS_ERROR = "error consulta carga";
    public static final String STATE_ON_PROCESSING = "ON_PROCESSING";
}
```

#### [MODIFY] [ProductMasterRepository.java (Puerto)](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/port/out/ProductMasterRepository.java)
> Agregar `countAllProducts()` para evitar cargar todos los registros a memoria.

```java
// MODIFICADO
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for fetching master product information from an external database.
 */
public interface ProductMasterRepository {
    Flux<ProductMaestro> getAllProducts();

    // NUEVO
    /** Cuenta el total de productos maestros sin cargarlos en memoria. */
    Mono<Long> countAllProducts();
}
```

#### [MODIFY] [ProductMasterR2dbcAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/masterdb/ProductMasterR2dbcAdapter.java)
```java
// MODIFICADO
package com.example.fileprocessor.infrastructure.drivenadapters.masterdb;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ProductMasterR2dbcAdapter implements ProductMasterRepository {

    private static final Logger LOGGER = Logger.getLogger(ProductMasterR2dbcAdapter.class.getName());

    @Qualifier("masterDatabaseClient")
    private final DatabaseClient masterDatabaseClient;

    @Override
    public Flux<ProductMaestro> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            String dateInit = ctx.getOrDefault(ApiConstants.HEADER_DATE_INIT, "");
            String dateEnd = ctx.getOrDefault(ApiConstants.HEADER_DATE_END, "");

            LOGGER.info(() -> "Fetching master products from EXTERNAL DATABASE. Filters - dateInit: " + dateInit + ", dateEnd: " + dateEnd);

            String baseSql = "SELECT id, id_producto, nombre, fecha_cargue, estado, carpeta_origen, pais_origen FROM productos_maestros";

            if (dateInit != null && !dateInit.isBlank() && dateEnd != null && !dateEnd.isBlank()) {
                baseSql += " WHERE fecha_cargue BETWEEN :dateInit AND :dateEnd";
                java.time.LocalDateTime start = parseDateTime(dateInit, false);
                java.time.LocalDateTime end = parseDateTime(dateEnd, true);
                
                return masterDatabaseClient.sql(baseSql)
                    .bind("dateInit", start)
                    .bind("dateEnd", end)
                    .map((row, metadata) -> ProductMaestro.builder()
                        .id(row.get("id", Long.class))
                        .productId(row.get("id_producto", String.class))
                        .name(row.get("nombre", String.class))
                        .loadDate(row.get("fecha_cargue", java.time.LocalDateTime.class))
                        .state(row.get("estado", String.class))
                        .originFolder(row.get("carpeta_origen", String.class))
                        .originCountry(row.get("pais_origen", String.class))
                        .build())
                    .all();
            }

            return masterDatabaseClient.sql(baseSql)
                .map((row, metadata) -> ProductMaestro.builder()
                    .id(row.get("id", Long.class))
                    .productId(row.get("id_producto", String.class))
                    .name(row.get("nombre", String.class))
                    .loadDate(row.get("fecha_cargue", java.time.LocalDateTime.class))
                    .state(row.get("estado", String.class))
                    .originFolder(row.get("carpeta_origen", String.class))
                    .originCountry(row.get("pais_origen", String.class))
                    .build())
                .all();
        });
    }

    // NUEVO
    @Override
    public Mono<Long> countAllProducts() {
        return masterDatabaseClient.sql("SELECT COUNT(*) FROM productos_maestros")
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private java.time.LocalDateTime parseDateTime(String dateStr, boolean endOfDay) {
        try {
            String trimmed = dateStr.trim();
            if (trimmed.contains(" ") || trimmed.contains("T")) {
                String clean = trimmed.replace("T", " ");
                if (clean.length() > 19) {
                    clean = clean.substring(0, 19);
                }
                return java.time.LocalDateTime.parse(clean, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            java.time.LocalDate date = java.time.LocalDate.parse(trimmed);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (Exception e) {
            return endOfDay ? java.time.LocalDateTime.now() : java.time.LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }
}
```

#### [NEW] `StateCount.java` — en paquete de dominio
```java
// NUEVO
package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StateCount {
    private String state;
    private Long total;
}
```

#### [MODIFY] [DocumentRepository.java (Puerto de dominio)](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/port/out/DocumentRepository.java)
```java
// MODIFICADO
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.StateCount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Port for managing document metadata and lifecycle states.
 */
public interface DocumentRepository {
    Mono<Document> save(Document document);

    /**
     * Finds documents for the current day.
     */
    Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay);

    /**
     * Updates document state and retry count.
     * @param doc The document with new values.
     * @param expectedState The previous state required for the update to succeed.
     */
    Mono<Long> updateStateAndRetry(Document doc, String expectedState);

    /**
     * Checks if a document with the given productId and documentId already exists.
     */
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

    // NUEVO
    /** Cuenta documentos creados hoy por caso de uso (sync status) */
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    // NUEVO
    /** Agrupa documentos por estado hoy por caso de uso (process status) */
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);

    // NUEVO
    /** Cuenta documentos en un estado específico que llevan más de cierto tiempo hoy */
    Mono<Long> countStaleDocuments(String state, String useCase, LocalDateTime startOfDay, LocalDateTime thresholdTime);
}
```

#### [MODIFY] [DocumentRepository.java (R2DBC)](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentRepository.java)
```java
// MODIFICADO
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {

    @Query("SELECT * FROM documentos WHERE estado_sincronizacion = $1 AND caso_uso = $2 AND fecha_carga >= $3")
    Flux<DocumentEntity> findByStateAndUseCaseToday(String estado, String casoUso, LocalDateTime startOfDay);

    @Query("SELECT COUNT(*) > 0 FROM documentos WHERE id_producto = $1 AND id_documento = $2")
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

    // NUEVO
    /** Cuenta documentos creados hoy por caso de uso (sync status) */
    @Query("SELECT COUNT(*) FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2")
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    // NUEVO
    /** Agrupa documentos por estado hoy por caso de uso (process status) */
    @Query("SELECT estado_sincronizacion AS state, COUNT(*) AS total FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2 GROUP BY estado_sincronizacion")
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);

    // NUEVO
    /** Cuenta documentos en un estado específico que llevan más de cierto tiempo hoy */
    @Query("SELECT COUNT(*) FROM documentos WHERE estado_sincronizacion = $1 AND caso_uso = $2 AND fecha_carga >= $3 AND fecha_carga_actualizacion < $4")
    Mono<Long> countStaleDocuments(String state, String useCase, LocalDateTime startOfDay, LocalDateTime thresholdTime);
}
```

#### [MODIFY] [DocumentR2dbcAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentR2dbcAdapter.java)
```java
// MODIFICADO
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.common.AbstractReactiveAdapterOperation;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class DocumentR2dbcAdapter
        extends
        AbstractReactiveAdapterOperation<DocumentEntity, Document, Long, com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository>
        implements DocumentRepository {

    public DocumentR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository repository,
            ObjectMapper mapper) {
        super(repository, mapper, d -> mapper.map(d, Document.class), DocumentEntity.class);
    }

    @Override
    public Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay) {
        return doQueryMany(() -> repository.findByStateAndUseCaseToday(state, useCase, startOfDay));
    }

    @Override
    public Mono<Long> updateStateAndRetry(Document doc, String expectedState) {
        return repository.findById(doc.getId())
            .flatMap(entity -> {
                // Atomic state validation
                if (!expectedState.equals(entity.getState())) {
                    return Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                        "No se pudo actualizar el documento: el estado actual [" + entity.getState() + 
                        "] no coincide con el esperado [" + expectedState + "]", 
                        "STATE_MISMATCH"));
                }

                // Map updates from domain aggregate
                entity.setState(doc.getState());
                entity.setRetryCount(doc.getRetryCountSafe());
                entity.setUpdatedAt(LocalDateTime.now());
                entity.setSyncMessage(doc.getSyncMessage());
                entity.setHomologationFolder(doc.getHomologationFolder());
                entity.setHomologationCountry(doc.getHomologationCountry());
                entity.setCategoriaHomologada(doc.getCategoriaHomologada());

                return repository.save(entity).thenReturn(1L);
            });
    }

    @Override
    public Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId) {
        return repository.existsByProductIdAndDocumentId(productId, documentId);
    }

    // NUEVO
    @Override
    public Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsCreatedToday(startOfDay, useCase);
    }

    // NUEVO
    @Override
    public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsGroupedByStateToday(startOfDay, useCase);
    }

    // NUEVO
    @Override
    public Mono<Long> countStaleDocuments(String state, String useCase, LocalDateTime startOfDay, LocalDateTime thresholdTime) {
        return repository.countStaleDocuments(state, useCase, startOfDay, thresholdTime);
    }
}
```

---

### B. Capa de Dominio (Casos de Uso)

#### [NEW] `GetSyncStatusUseCase.java`
```java
// NUEVO
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetSyncStatusUseCase {

    private final ProductMasterRepository productMasterRepository;
    private final DocumentRepository documentRepository;

    public Mono<String> execute(String useCase, String traceId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        return Mono.zip(
                productMasterRepository.countAllProducts().defaultIfEmpty(0L),
                documentRepository.countDocumentsCreatedToday(startOfDay, useCase).defaultIfEmpty(0L)
        ).map(tuple -> {
            long masterCount = tuple.getT1();
            long localCount = tuple.getT2();

            return (localCount >= masterCount)
                    ? ApiConstants.STATUS_OK
                    : ApiConstants.STATUS_ERROR;
        });
    }
}
```

#### [NEW] `GetProcessStatusUseCase.java`
```java
// NUEVO
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RequiredArgsConstructor
public class GetProcessStatusUseCase {

    private final DocumentRepository documentRepository;

    public Mono<String> execute(String useCase, String traceId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(1);

        return Mono.zip(
                documentRepository.countDocumentsGroupedByStateToday(startOfDay, useCase).collectList(),
                documentRepository.countStaleDocuments(
                        ProcessingResultCodes.ON_PROCESSING.name(),
                        useCase,
                        startOfDay,
                        thresholdTime
                ).defaultIfEmpty(0L)
        ).map(tuple -> {
            List<StateCount> list = tuple.getT1();
            long staleCount = tuple.getT2();

            long processed = 0;
            long pending = 0;
            long technicalFailures = 0;

            for (var row : list) {
                String state = row.getState();
                long count = row.getTotal();

                // isBusinessRule() incluye ERR_DUPLICATED_DOC; NO_SUCURSAL se excluye explícitamente
                if (ProcessingResultCodes.NO_SUCURSAL.name().equals(state)
                        || ProcessingResultCodes.isBusinessRule(state)) {
                    continue;
                }

                if (ProcessingResultCodes.ON_PROCESSING.name().equals(state)) {
                    // Si están en ON_PROCESSING, descontamos los que llevan más de 1 hora
                    long active = Math.max(0, count - staleCount);
                    pending += active;
                } else if (ProcessingResultCodes.PROCESSED.name().equals(state)) {
                    processed += count;
                } else {
                    technicalFailures += count;
                }
            }

            // Los documentos en ON_PROCESSING que superaron la hora cuentan como fallos técnicos
            technicalFailures += staleCount;

            long totalApplicable = processed + pending + technicalFailures;

            if (totalApplicable == 0 || pending > 0) {
                return ApiConstants.STATUS_OK;
            }
            return (technicalFailures > 0)
                    ? ApiConstants.STATUS_ERROR
                    : ApiConstants.STATUS_OK;
        });
    }
}
```

---

### C. Capa de Aplicación (DomainConfig)

#### [MODIFY] [DomainConfig.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/application/service/config/DomainConfig.java)
```java
// MODIFICADO
package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.GetSyncStatusUseCase;
import com.example.fileprocessor.domain.usecase.GetProcessStatusUseCase;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProcessorsProperties.class)
public class DomainConfig {

    @Bean
    @ConditionalOnBean(SoapGateway.class)
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            ProcessorsProperties properties) {
        return new SoapDocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            soapGateway,
            new RulesBussinesService(properties.soap()),
            homologationRepository
        );
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            ProcessorsProperties properties) {
        return new S3DocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            s3Gateway,
            new RulesBussinesService(properties.s3())
        );
    }

    @Bean
    public SyncDocumentsUseCase syncDocumentsUseCase(
            DocumentRepository documentRepository,
            ProductMasterRepository productMasterRepository,
            ProductRestGateway productRestGateway,
            ProductLocalRepository productLocalRepository) {
        return new SyncDocumentsUseCase(
            documentRepository,
            productMasterRepository,
            productRestGateway,
            productLocalRepository
        );
    }

    // NUEVO
    @Bean
    public GetSyncStatusUseCase getSyncStatusUseCase(
            ProductMasterRepository productMasterRepository,
            DocumentRepository documentRepository) {
        return new GetSyncStatusUseCase(productMasterRepository, documentRepository);
    }

    // NUEVO
    @Bean
    public GetProcessStatusUseCase getProcessStatusUseCase(
            DocumentRepository documentRepository) {
        return new GetProcessStatusUseCase(documentRepository);
    }
}
```

---

### D. Capa de Presentación

#### [MODIFY] [ProductRoutes.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/ProductRoutes.java)
```java
// MODIFICADO
package com.example.fileprocessor.infrastructure.entrypoints.rest;

import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.RestApiPaths;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.PathProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@RequiredArgsConstructor
public class ProductRoutes {

    private final PathProperties pathProperties;

    @Bean
    public RouterFunction<ServerResponse> processPendingProducts(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET(pathProperties.API_V1_PRODUCTS()), handler::processPendingProducts)
            );
    }

    @Bean
    public RouterFunction<ServerResponse> syncProducts(ProductHandler handler) {
        return nest(
                path(pathProperties.basePath()),
                route(GET(pathProperties.API_V1_PRODUCTS_SYNC()), handler::syncProducts)
        );
    }

    // NUEVO
    @Bean
    public RouterFunction<ServerResponse> syncStatusRoute(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET("/products/sync/status/{type_job}"), handler::getSyncStatus)
        );
    }

    // NUEVO
    @Bean
    public RouterFunction<ServerResponse> processStatusRoute(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET("/products/process/status/{type_job}"), handler::getProcessStatus)
        );
    }
}
```

#### [MODIFY] [ProductHandler.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java)
```java
// MODIFICADO
package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.GetSyncStatusUseCase;
import com.example.fileprocessor.domain.usecase.GetProcessStatusUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.*;

@Component
public class ProductHandler {

    private static final Logger LOGGER = Logger.getLogger(ProductHandler.class.getName());

    private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
    private final ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
    private final SyncDocumentsUseCase syncDocumentsUseCase;
    // NUEVO
    private final GetSyncStatusUseCase getSyncStatusUseCase;
    // NUEVO
    private final GetProcessStatusUseCase getProcessStatusUseCase;

    public ProductHandler(
            SoapDocumentProcessingUseCase soapDocumentUseCase,
            ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider,
            SyncDocumentsUseCase syncDocumentsUseCase,
            // NUEVO
            GetSyncStatusUseCase getSyncStatusUseCase,
            // NUEVO
            GetProcessStatusUseCase getProcessStatusUseCase) {
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
        this.syncDocumentsUseCase = syncDocumentsUseCase;
        this.getSyncStatusUseCase = getSyncStatusUseCase;
        this.getProcessStatusUseCase = getProcessStatusUseCase;
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();

        Context context = Context.of(
            TYPE_JOB, request.pathVariables().containsKey(TYPE_JOB) ? request.pathVariable(TYPE_JOB) : "default",
            HEADER_TRACE_ID, headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString()),
            HEADER_USE_CASE, headers.getOrDefault(HEADER_USE_CASE, "default")
        );

        return Mono.deferContextual(ctx -> {
            String useCase = ctx.get(TYPE_JOB);
            String traceId = ctx.get(HEADER_TRACE_ID);

            LOGGER.log(Level.INFO, "Starting pending documents processing, traceId: {0}, useCase: {1}", new Object[]{traceId, useCase});

            getProcessor(useCase).executePendingDocuments()
                .doOnNext(result -> LOGGER.log(Level.INFO, "Document processed: correlationId={0}, status={1}",
                    new Object[]{result.getCorrelationId(), result.getStatus()}))
                .doOnError(error -> LOGGER.log(Level.SEVERE, "Processing failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnComplete(() -> LOGGER.log(Level.INFO, "Pending documents processing completed for traceId: {0}", new Object[]{traceId}))
                .contextWrite(ctx)
                .subscribe();

            return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("status", "OK", "message", "Document processing initiated"));
        }).contextWrite(context);
    }

    public Mono<ServerResponse> syncProducts(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();

        Context context = Context.of(
                TYPE_JOB, request.pathVariables().containsKey(TYPE_JOB) ? request.pathVariable(TYPE_JOB) : "default",
                HEADER_TRACE_ID, headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString()),
                HEADER_USE_CASE, headers.getOrDefault(HEADER_USE_CASE, "default"),
                HEADER_DATE_INIT, request.queryParam(ApiConstants.HEADER_DATE_INIT).orElse(""),
                HEADER_DATE_END, request.queryParam(ApiConstants.HEADER_DATE_END).orElse("")
        );

        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(HEADER_TRACE_ID);
            String useCase = ctx.get(HEADER_USE_CASE);
            String dateInitVal = ctx.get(ApiConstants.HEADER_DATE_INIT);
            String dateEndVal = ctx.get(ApiConstants.HEADER_DATE_END);

            LOGGER.log(Level.INFO, "Starting document sync, traceId: {0}, useCase: {1}, dateInit: {2}, dateEnd: {3}",
                    new Object[]{traceId, useCase, dateInitVal, dateEndVal});
            syncDocumentsUseCase.execute(useCase)
                .doOnError(error -> LOGGER.log(Level.SEVERE, "Document sync failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnSuccess(v -> LOGGER.log(Level.INFO, "Document sync completed for traceId: {0}", new Object[]{traceId}))
                .contextWrite(ctx)
                .subscribe();
            return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("status", "OK", "message", "Document sync initiated"));
        }).contextWrite(context);
    }

    // NUEVO
    public Mono<ServerResponse> getSyncStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(TYPE_JOB);

        return getSyncStatusUseCase.execute(useCase, traceId)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }

    // NUEVO
    public Mono<ServerResponse> getProcessStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(TYPE_JOB);

        return getProcessStatusUseCase.execute(useCase, traceId)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }

    AbstractDocumentProcessingUseCase getProcessor(String processorType) {
        return switch (processorType) {
            case ApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
            case ApiConstants.PROCESSOR_S3 -> {
                S3DocumentProcessingUseCase s3UseCase = s3DocumentUseCaseProvider.getIfAvailable();
                if (s3UseCase == null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "S3 processor not available - enable 's3' profile");
                }
                yield s3UseCase;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown processor type: '" + processorType + "'. Valid values: soap, s3");
        };
    }
}
```

---

## 3. Pruebas Unitarias

### `GetSyncStatusUseCaseTest.java`
```java
// NUEVO
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSyncStatusUseCaseTest {

    @Mock private ProductMasterRepository productMasterRepository;
    @Mock private DocumentRepository documentRepository;

    private GetSyncStatusUseCase useCase() {
        return new GetSyncStatusUseCase(productMasterRepository, documentRepository);
    }

    // ✅ local < master → STATUS_ERROR (sincronización incompleta)
    @Test
    void execute_whenLocalLessThanMaster_returnsError() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(5L));

        StepVerifier.create(useCase().execute("retention", "t1"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ local == master → STATUS_OK (completado)
    @Test
    void execute_whenLocalEqualsMaster_returnsOne() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(10L));

        StepVerifier.create(useCase().execute("retention", "t2"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ local > master → STATUS_OK
    @Test
    void execute_whenLocalExceedsMaster_returnsOne() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(5L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(8L));

        StepVerifier.create(useCase().execute("retention", "t3"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ master vacío (0) → STATUS_OK
    @Test
    void execute_whenMasterEmpty_returnsOne() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(0L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase().execute("retention", "t4"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }
}
```

### `GetProcessStatusUseCaseTest.java`
```java
// NUEVO
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProcessStatusUseCaseTest {

    @Mock private DocumentRepository documentRepository;

    private GetProcessStatusUseCase useCase() {
        return new GetProcessStatusUseCase(documentRepository);
    }

    private void mockCounts(List<StateCount> counts) {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), anyString()))
                .thenReturn(Flux.fromIterable(counts));
    }

    private void mockStale(long count) {
        when(documentRepository.countStaleDocuments(anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(count));
    }

    // ✅ sin documentos hoy → STATUS_OK
    @Test
    void execute_whenNoDocumentsToday_returnsOne() {
        mockCounts(List.of());
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t1"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ❌ documentos PENDING no se consideran activos → STATUS_ERROR (o no en curso si no hay ON_PROCESSING activo)
    @Test
    void execute_whenPendingDocumentsNoOnProcessing_returnsError() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PENDING.name(), 5L)));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t2"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ❌ documentos IN_PROGRESS no se consideran activos → STATUS_ERROR (o no en curso si no hay ON_PROCESSING activo)
    @Test
    void execute_whenInProgressNoOnProcessing_returnsError() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.IN_PROGRESS.name(), 3L)));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t3"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ ON_PROCESSING activo (dentro del tiempo) → STATUS_OK
    @Test
    void execute_whenOnProcessingActive_returnsOne() {
        mockCounts(List.of(new StateCount("ON_PROCESSING", 4L)));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t_op1"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ ON_PROCESSING stuck (más de 1 hora) → STATUS_ERROR
    @Test
    void execute_whenOnProcessingStaleNoOtherPending_returnsError() {
        mockCounts(List.of(new StateCount("ON_PROCESSING", 2L)));
        mockStale(2L);

        StepVerifier.create(useCase().execute("retention", "t_op2"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ ON_PROCESSING mixto (algunos stuck, otros aún activos) → STATUS_OK (sigue en curso)
    @Test
    void execute_whenOnProcessingMixed_returnsOne() {
        mockCounts(List.of(new StateCount("ON_PROCESSING", 3L)));
        mockStale(1L);

        StepVerifier.create(useCase().execute("retention", "t_op3"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ todos procesados sin fallos → STATUS_OK
    @Test
    void execute_whenAllProcessedNoFailures_returnsOne() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PROCESSED.name(), 10L)));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t4"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ fallos técnicos sin pendientes → STATUS_ERROR
    @Test
    void execute_whenTechnicalFailuresNoPending_returnsError() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.PROCESSED.name(), 8L),
                new StateCount(ProcessingResultCodes.FAILED.name(), 2L)
        ));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t5"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ fallos técnicos pero aún hay ON_PROCESSING activos → STATUS_OK (aún en curso)
    @Test
    void execute_whenTechnicalFailuresAndStillActive_returnsOne() {
        mockCounts(List.of(
                new StateCount("ON_PROCESSING", 2L),
                new StateCount(ProcessingResultCodes.FAILED.name(), 1L)
        ));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t6"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ solo estados excluidos → STATUS_OK
    @Test
    void execute_whenOnlyExcludedStates_returnsOne() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.ERR_DUPLICATED_DOC.name(), 5L),
                new StateCount(ProcessingResultCodes.NO_SUCURSAL.name(), 3L)
        ));
        mockStale(0L);

        StepVerifier.create(useCase().execute("retention", "t7"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }
}
```

### `ProductHandlerStatusTest.java`
```java
// NUEVO
package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.GetProcessStatusUseCase;
import com.example.fileprocessor.domain.usecase.GetSyncStatusUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductHandlerStatusTest {

    @Mock private GetSyncStatusUseCase getSyncStatusUseCase;
    @Mock private GetProcessStatusUseCase getProcessStatusUseCase;
    @Mock private ServerRequest serverRequest;

    private ProductHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProductHandler(null, null, null, getSyncStatusUseCase, getProcessStatusUseCase);
    }

    private void mockRequestHeaders() {
        ServerRequest.Headers headers = Mockito.mock(ServerRequest.Headers.class);
        when(headers.asHttpHeaders()).thenReturn(new HttpHeaders());
        when(serverRequest.headers()).thenReturn(headers);
    }

    @Test
    void getSyncStatus_returnsOk() {
        mockRequestHeaders();
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getSyncStatusUseCase.execute(anyString(), anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_OK));

        StepVerifier.create(handler.getSyncStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }

    @Test
    void getProcessStatus_returnsOk() {
        mockRequestHeaders();
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getProcessStatusUseCase.execute(anyString(), anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_ERROR));

        StepVerifier.create(handler.getProcessStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }
}
```
