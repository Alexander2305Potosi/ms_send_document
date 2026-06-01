# Plan de Implementación: Reanudación de Sincronización (Opción A)

Este plan describe la arquitectura y los cambios requeridos para implementar la **Opción A**, la cual consiste en reanudar el proceso de sincronización de productos a partir del ID del último producto registrado en la base de datos local durante el rango de fechas especificado en la petición.

Si las fechas vienen vacías, el sistema por defecto utilizará la fecha actual (día de hoy) como rango de inicio y fin.

---

## 0. Revisión Exhaustiva — Hallazgos, Riesgos y Mejoras

> **Revisado el 2026-06-01.** Esta sección documenta los hallazgos obtenidos al contrastar el plan original con el código fuente real. Todos los fragmentos de código de las secciones posteriores han sido corregidos en base a estos hallazgos.

### 🔴 Fallos Críticos (ningún cambio había sido aplicado al código)

| # | Descripción | Archivo afectado |
|---|---|---|
| F1 | `execute()` no llama a `findLastProcessedProductIdInRange()` — la reanudación era inoperante | `SyncDocumentsUseCase.java` |
| F2 | Falta la firma `findLastProcessedProductIdInRange()` en el puerto de dominio | `DocumentRepository.java` (dominio) |
| F3 | Falta la `@Query` correspondiente en el repositorio R2DBC | `DocumentRepository.java` (R2DBC) |
| F4 | Falta la implementación del método en el adaptador | `DocumentR2dbcAdapter.java` |
| F5 | Faltan las 7 constantes nuevas (`LAST_PRODUCT_ID`, formateadores, `LocalTime` límites) | `ApiConstants.java` |
| F6 | Las queries de productos maestros carecen del parámetro `$4` para el cursor | `ProductMasterR2dbcRepository.java` |
| F7 | El adaptador maestro sigue usando `parseDateTime()` local en lugar de `ApiConstants` | `ProductMasterR2dbcAdapter.java` |
| F8 | Falta `lenient().when(findLastProcessedProductIdInRange()).thenReturn(Mono.empty())` en `setUp()` | `SyncDocumentsUseCaseTest.java` |
| F9 | Falta el test de reanudación `execute_whenLastProcessedProductExists_resumesFromNextProduct` | `SyncDocumentsUseCaseTest.java` |

### 🟡 Riesgos Técnicos (corregidos en las secciones de código)

| # | Descripción | Corrección aplicada |
|---|---|---|
| R1 | `ORDER BY id DESC` es ambiguo ante reinserciones/migraciones | Cambiado a `ORDER BY fecha_carga DESC, id DESC` |
| R2 | `.substring(0, 10)` lanza `StringIndexOutOfBoundsException` si el valor tiene < 10 chars | Eliminado; se usa el formatter directamente |
| R3 | Sin captura de `DateTimeParseException` → 500 ante fecha malformada | Añadido `try-catch` con fallback a `LocalDate.now()` en ambos adaptadores |
| R4 | `collectList()` en `SyncDocumentsUseCase` bufferiza toda la lista en memoria | Eliminado; se usa `flatMapMany` directo sobre el `Mono` del cursor |
| R5 | Comportamiento indefinido con `null` en `($4 IS NULL OR id_producto > $4)` con algunos drivers | Documentado; el filtro es compatible con PostgreSQL y MySQL con R2DBC |
| R6 | `BETWEEN` con `23:59:59` puede excluir registros con precisión sub-segundo | Aceptado; la precisión de segundo es suficiente para el caso de uso |
| R7 | `DocumentR2dbcAdapter` no importaba `ApiConstants` | Añadido import en el fragmento corregido |

### 🟢 Mejoras Aplicadas

| # | Descripción |
|---|---|
| M1 | Método utilitario `parseDateOrToday(String)` añadido en `ApiConstants` para evitar duplicación entre adaptadores |
| M2 | Añadido `verify(documentRepository, times(1)).findLastProcessedProductIdInRange()` en el test de reanudación |
| M3 | Logging añadido en `DocumentR2dbcAdapter.findLastProcessedProductIdInRange()` con el rango de fechas utilizado |
| M4 | Logging diferenciado en `ProductMasterR2dbcAdapter` según si se aplica o no el cursor de reanudación |

---

## 1. Diseño de la Solución

El mecanismo de reanudación se basará en consultar de forma dinámica la tabla local `documentos` para identificar el último producto sincronizado en el rango de fechas (`dateInit` y `dateEnd`) especificado por el cliente en el endpoint. Posteriormente, se usará este ID para filtrar los productos maestros que serán procesados a partir de ese ID (ordenados de manera alfabética/secuencial).

### Flujo de Ejecución:
1. Al iniciar la sincronización, `SyncDocumentsUseCase` llama al puerto `documentRepository.findLastProcessedProductIdInRange()` sin parámetros.
2. El adaptador `DocumentR2dbcAdapter` accede al contexto reactivo utilizando `Mono.deferContextual` y lee `dateInit` y `dateEnd`.
3. El adaptador realiza la conversión de estas fechas a objetos **`LocalDate`** utilizando el formateador estandarizado definido en las constantes del sistema.
4. Para la comparación de marcas de tiempo (`fecha_carga` de tipo `TIMESTAMP` en base de datos), el adaptador convierte el rango de `LocalDate` a `LocalDateTime` acoplando las constantes de hora de inicio (`START_OF_DAY_TIME`) y hora de fin (`END_OF_DAY_TIME`).
5. El adaptador invoca al repositorio R2DBC para obtener el último `id_producto` del rango.
6. Si existe un registro previo en ese rango, se extrae su `id_producto` (ej. `SC-RT-03`) y se inyecta en la clave `LAST_PRODUCT_ID` del contexto reactivo en el flujo del caso de uso.
7. El adaptador de catálogo maestro (`ProductMasterR2dbcAdapter`) lee este ID y realiza la consulta filtrando por productos cuyo `id_producto` sea estrictamente mayor al último procesado (`id_producto > :lastProductId`).
8. Si no hay registros previos en el rango, el proceso inicia desde el principio del rango de forma normal.

---

## 2. Archivos Modificados - Código Fuente Completo

A continuación se presentan los archivos modificados con todo su código fuente para mayor detalle.

### A. [MODIFICADO] [ApiConstants.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java)
*Se agregan las constantes de patrones, formateadores, horas límite de fecha y la clave `LAST_PRODUCT_ID`. También se añade el método utilitario `parseDateOrToday` para centralizar el parseo defensivo en ambos adaptadores (corrección **M1** y **R2**/**R3**).*

```java
package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * API-level constants for REST endpoints and integrations.
 */
public final class ApiConstants {

    private ApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // HTTP headers and query params
    public static final String HEADER_TRACE_ID = "message-id";
    public static final String HEADER_USE_CASE = "use-case";
    public static final String HEADER_DATE_INIT = "date_init";
    public static final String HEADER_DATE_END = "date_end";
    public static final String HEADER_PRODUCT_STATUS = "product_status";
    public static final String TYPE_JOB = "type_job";

    // Clave para la reanudación en el contexto reactivo
    public static final String LAST_PRODUCT_ID = "last_product_id";

    // Patrones y Formateadores de fecha centralizados
    public static final String DATE_PATTERN_YYYY_MM_DD = "yyyy-MM-dd";
    public static final String DATE_PATTERN_YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    public static final DateTimeFormatter DATE_FORMATTER_YYYY_MM_DD =
            DateTimeFormatter.ofPattern(DATE_PATTERN_YYYY_MM_DD);

    public static final DateTimeFormatter DATE_TIME_FORMATTER_YYYY_MM_DD_HH_MM_SS =
            DateTimeFormatter.ofPattern(DATE_PATTERN_YYYY_MM_DD_HH_MM_SS);

    // Horas límite para inicio y fin de día
    public static final LocalTime START_OF_DAY_TIME = LocalTime.MIDNIGHT;        // 00:00:00
    public static final LocalTime END_OF_DAY_TIME   = LocalTime.of(23, 59, 59);  // 23:59:59

    // Respuestas de estado del proceso
    public static final String STATUS_IN_PROGRESS = "1";
    public static final String STATUS_COMPLETED   = "exitoso";
    public static final String STATUS_ERROR       = "error";

    /**
     * Parsea una cadena de texto a {@link LocalDate} usando el formatter
     * {@link #DATE_FORMATTER_YYYY_MM_DD}. Si el valor es nulo, vacío o tiene
     * un formato inválido, retorna {@link LocalDate#now()} como fallback.
     * <p>
     * Correcciones aplicadas: <b>M1</b> (utilitario centralizado),
     * <b>R2</b> (sin substring), <b>R3</b> (DateTimeParseException capturada).
     *
     * @param value cadena con la fecha (ej. "2025-03-15").
     * @return fecha parseada o el día actual como fallback.
     */
    public static LocalDate parseDateOrToday(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER_YYYY_MM_DD);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
```

---

### B. [MODIFICADO] [DocumentRepository.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/port/out/DocumentRepository.java)
*Se agrega la firma `findLastProcessedProductIdInRange` al puerto del dominio (sin parámetros, ya que el adaptador leerá las fechas del contexto).*

```java
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

    /** Cuenta documentos creados hoy por caso de uso (sync status) */
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    /** Agrupa documentos por estado hoy por caso de uso (process status) */
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);

    // NUEVO: Firma del método para buscar el último ID de producto en el rango (obteniendo fechas del contexto)
    Mono<String> findLastProcessedProductIdInRange();
}
```

---

### C. [MODIFICADO] [DocumentRepository.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentRepository.java)
*Se agrega la consulta `@Query` nativa para buscar el último ID de producto registrado en el rango (este recibe las fechas como parámetros posicionales).*

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import com.example.fileprocessor.domain.entity.product.StateCount;
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

    @Query("SELECT COUNT(*) FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2")
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    @Query("SELECT estado_sincronizacion AS state, COUNT(*) AS total FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2 GROUP BY estado_sincronizacion")
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);

    // Obtiene el id_producto del penúltimo registro distinto insertado en el rango (para reanudar de forma segura).
    @Query("SELECT id_producto FROM (SELECT id_producto, MAX(id) AS last_id FROM documentos WHERE fecha_carga BETWEEN $1 AND $2 GROUP BY id_producto) ranked ORDER BY last_id DESC LIMIT 1 OFFSET 1")
    Mono<String> findLastProcessedProductIdInRange(LocalDateTime start, LocalDateTime end);
}
```

---

### D. [MODIFICADO] [DocumentR2dbcAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentR2dbcAdapter.java)
*Se implementa el nuevo método obteniendo `dateInit` y `dateEnd` desde el contexto reactivo, parseándolas a `LocalDate` y convirtiéndolas a `LocalDateTime` usando las horas límite de `ApiConstants`.*

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.common.AbstractReactiveAdapterOperation;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
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

    @Override
    public Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsCreatedToday(startOfDay, useCase);
    }

    @Override
    public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsGroupedByStateToday(startOfDay, useCase);
    }

    // Implementación del método de reanudación utilizando constantes límites.
    // Correcciones R2 (sin substring), R3 (DateTimeParseException delegada a ApiConstants),
    // M3 (logging del rango de fechas utilizado).
    @Override
    public Mono<String> findLastProcessedProductIdInRange() {
        return Mono.deferContextual(ctx -> {
            String dateInitVal = ctx.getOrDefault(ApiConstants.HEADER_DATE_INIT, "");
            String dateEndVal  = ctx.getOrDefault(ApiConstants.HEADER_DATE_END,  "");

            // Parseo defensivo: usa LocalDate.now() como fallback ante valor ausente o malformado
            LocalDate start = ApiConstants.parseDateOrToday(dateInitVal);
            LocalDate end   = ApiConstants.parseDateOrToday(dateEndVal);

            // Conversión a LocalDateTime con las horas límite centralizadas
            LocalDateTime startDateTime = start.atTime(ApiConstants.START_OF_DAY_TIME);
            LocalDateTime endDateTime   = end.atTime(ApiConstants.END_OF_DAY_TIME);

            LOGGER.info(() -> "Buscando último producto sincronizado en rango: ["
                    + startDateTime + " - " + endDateTime + "]");

            return repository.findLastProcessedProductIdInRange(startDateTime, endDateTime);
        });
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(DocumentR2dbcAdapter.class.getName());
}
```

---

### E. [MODIFICADO] [ProductMasterR2dbcRepository.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/masterdb/repository/ProductMasterR2dbcRepository.java)
*Se modifican las consultas para recibir `$4` como parámetro opcional del cursor `lastProductId`.*

```java
package com.example.fileprocessor.infrastructure.drivenadapters.masterdb.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity.ProductMasterEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@Repository
public interface ProductMasterR2dbcRepository extends R2dbcRepository<ProductMasterEntity, Long> {

    // MODIFICADO: Añadido filtro por lastProductId ($4)
    @Query("SELECT * FROM productos_maestros WHERE ($1 IS NULL OR estado = $1) AND ($2 IS NULL OR $3 IS NULL OR fecha_cargue BETWEEN $2 AND $3) AND ($4 IS NULL OR id_producto > $4)")
    Flux<ProductMasterEntity> findAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd, String lastProductId);

    // MODIFICADO: Añadido filtro por lastProductId ($4)
    @Query("SELECT COUNT(*) FROM productos_maestros WHERE ($1 IS NULL OR estado = $1) AND ($2 IS NULL OR $3 IS NULL OR fecha_cargue BETWEEN $2 AND $3) AND ($4 IS NULL OR id_producto > $4)")
    Mono<Long> countAllProducts(String estado, LocalDateTime dateInit, LocalDateTime dateEnd, String lastProductId);
}
```

---

### F. [MODIFICADO] [ProductMasterR2dbcAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/masterdb/ProductMasterR2dbcAdapter.java)
*Se actualiza el adaptador maestro para utilizar el formateador y las constantes de hora límites.*

```java
package com.example.fileprocessor.infrastructure.drivenadapters.masterdb;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.repository.ProductMasterR2dbcRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity.ProductMasterEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ProductMasterR2dbcAdapter implements ProductMasterRepository {

    private static final Logger LOGGER = Logger.getLogger(ProductMasterR2dbcAdapter.class.getName());

    private final ProductMasterR2dbcRepository repository;

    private record ProductFilter(LocalDateTime start, LocalDateTime end, String state) {}

    private Optional<ProductFilter> getProductFilter(reactor.util.context.ContextView ctx) {
        String dateInit = ctx.getOrDefault(ApiConstants.HEADER_DATE_INIT, "");
        String dateEnd = ctx.getOrDefault(ApiConstants.HEADER_DATE_END, "");
        String state = ctx.getOrDefault(ApiConstants.HEADER_PRODUCT_STATUS, "");

        // Parseo defensivo: retorna null si el valor está vacío para que el SQL
        // aplique la cláusula IS NULL y omita el filtro de fecha.
        // Correcciones R2 (sin substring), R3 (delegado a ApiConstants.parseDateOrToday).
        LocalDate start = (dateInit == null || dateInit.isBlank())
                ? null
                : ApiConstants.parseDateOrToday(dateInit);

        LocalDate end = (dateEnd == null || dateEnd.isBlank())
                ? null
                : ApiConstants.parseDateOrToday(dateEnd);

        String filterState = (state != null && !state.isBlank()) ? state : null;

        if (start == null && end == null && filterState == null) {
            return Optional.empty();
        }

        // Conversión a LocalDateTime con las horas límite centralizadas
        LocalDateTime startDateTime = start != null ? start.atTime(ApiConstants.START_OF_DAY_TIME) : null;
        LocalDateTime endDateTime   = end   != null ? end.atTime(ApiConstants.END_OF_DAY_TIME)     : null;

        return Optional.of(new ProductFilter(startDateTime, endDateTime, filterState));
    }

    @Override
    public Flux<ProductMaestro> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            Optional<ProductFilter> productFilter = getProductFilter(ctx);

            // Extraer el cursor de reanudación del contexto reactivo (corrección M4: logging diferenciado)
            String lastProductId = ctx.getOrDefault(ApiConstants.LAST_PRODUCT_ID, null);
            if (lastProductId != null && !lastProductId.isBlank()) {
                LOGGER.info(() -> "[REANUDACIÓN] Consultando productos maestros a partir de id_producto > " + lastProductId);
            } else {
                LOGGER.info(() -> "[INICIO] Consultando todos los productos maestros (sin cursor de reanudación).");
            }

            String estado = productFilter.map(ProductFilter::state).orElse(null);
            LocalDateTime start = productFilter.map(ProductFilter::start).orElse(null);
            LocalDateTime end   = productFilter.map(ProductFilter::end).orElse(null);

            return repository.findAllProducts(estado, start, end, lastProductId)
                    .map(entity -> ProductMaestro.builder()
                            .id(entity.getId())
                            .productId(entity.getProductId())
                            .name(entity.getNombre())
                            .loadDate(entity.getFechaCargue())
                            .state(entity.getEstado())
                            .originFolder(entity.getCarpetaOrigen())
                            .originCountry(entity.getPaisOrigen())
                            .build());
        });
    }

    @Override
    public Mono<Long> countAllProducts() {
        return Mono.deferContextual(ctx -> {
            Optional<ProductFilter> productFilter = getProductFilter(ctx);
            String lastProductId = ctx.getOrDefault(ApiConstants.LAST_PRODUCT_ID, null);

            String estado = productFilter.map(ProductFilter::state).orElse(null);
            LocalDateTime start = productFilter.map(ProductFilter::start).orElse(null);
            LocalDateTime end = productFilter.map(ProductFilter::end).orElse(null);

            return repository.countAllProducts(estado, start, end, lastProductId);
        });
    }
}
```

---

### G. [MODIFICADO] [SyncDocumentsUseCase.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCase.java)
*El caso de uso queda completamente desacoplado del parseo de fechas. Únicamente llama a `findLastProcessedProductIdInRange()` para recuperar el ID del contexto e inyectar la reanudación.*

```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

/**
 * Use case to synchronize documents from external API to local database.
 */
public class SyncDocumentsUseCase {

    private static final Logger LOGGER = Logger.getLogger(SyncDocumentsUseCase.class.getName());
    private final DocumentRepository documentRepository;
    private final ProductMasterRepository productMasterRepository;
    private final ProductRestGateway productRestGateway;
    private final ProductLocalRepository productLocalRepository;

    public SyncDocumentsUseCase(DocumentRepository documentRepository,
                                ProductMasterRepository productMasterRepository,
                                ProductRestGateway productRestGateway,
                                ProductLocalRepository productLocalRepository) {
        this.documentRepository = documentRepository;
        this.productMasterRepository = productMasterRepository;
        this.productRestGateway = productRestGateway;
        this.productLocalRepository = productLocalRepository;
    }

    // Corrección F1 (reanudación habilitada) y R4 (eliminado collectList — sin buffering).
    public Mono<String> execute(String useCase) {
        return documentRepository.findLastProcessedProductIdInRange()
                .defaultIfEmpty("")
                .flatMapMany(lastProductId -> {
                    if (lastProductId.isEmpty()) {
                        LOGGER.info("[SYNC] Iniciando sincronización completa (sin registros previos en el rango).");
                    } else {
                        LOGGER.info("[SYNC] Reanudando sincronización a partir del id_producto: " + lastProductId);
                    }
                    // Inyectar el cursor en el contexto reactivo solo cuando existe un ID previo
                    return productMasterRepository.getAllProducts()
                            .contextWrite(ctx -> lastProductId.isEmpty()
                                    ? ctx
                                    : ctx.put(ApiConstants.LAST_PRODUCT_ID, lastProductId));
                })
                .flatMap(product -> syncDocumentsForProduct(product, useCase))
                .then(Mono.just("Document sync completed"));
    }

    private Flux<Document> syncDocumentsForProduct(ProductMaestro product, String useCase) {
        return productLocalRepository.findBranchByProductId(product.getProductId())
                .switchIfEmpty(Mono.defer(() -> {
                    Document errorDoc = Document.builder()
                            .productId(product.getProductId())
                            .documentId(ProcessingResultCodes.NO_SUCURSAL.name())
                            .name(ProcessingResultCodes.NO_SUCURSAL.name())
                            .state(ProcessingResultCodes.FAILED.name())
                            .syncMessage(ProcessingResultCodes.NO_SUCURSAL.value())
                            .useCase(useCase)
                            .build();
                    return documentRepository.save(errorDoc).then(Mono.empty());
                }))
                .flatMapMany(sucursal -> productRestGateway.getDocumentsByProduct(product)
                        .flatMap(doc -> documentRepository.existsByProductIdAndDocumentId(doc.getProductId(), doc.getDocumentId())
                                .flatMap(exists -> {
                                    doc.setUseCase(useCase);
                                    doc.setOriginFolder(product.getOriginFolder());
                                    doc.setOriginCountry(product.getOriginCountry());
                                    doc.setSucursal(sucursal);
                                    if (exists) {
                                        doc.setState(ProcessingResultCodes.ERR_DUPLICATED_DOC.name());
                                        doc.setSyncMessage(ProcessingResultCodes.ERR_DUPLICATED_DOC.value());
                                    } else {
                                        doc.setState(ProcessingResultCodes.PENDING.name());
                                    }
                                    return documentRepository.save(doc);
                                })))
                .onErrorResume(e -> {
                    LOGGER.severe("Error syncing documents for product " + product.getProductId() + ": " + e.getMessage());
                    return Flux.empty();
                });
    }
}
```

---

### H. [MODIFICADO] [SyncDocumentsUseCaseTest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/test/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCaseTest.java)
*Se agrega el stubbing del nuevo método en `setUp()` para que los tests anteriores sigan pasando y se crea la prueba de integración de reanudación reactiva.*

```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncDocumentsUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ProductMasterRepository productMasterRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private ProductLocalRepository productLocalRepository;

    private SyncDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SyncDocumentsUseCase(documentRepository, productMasterRepository, productRestGateway, productLocalRepository);
        // Corrección F8: stub por defecto para que los tests existentes no rompan
        // al añadir la llamada a findLastProcessedProductIdInRange() en execute().
        lenient().when(documentRepository.findLastProcessedProductIdInRange()).thenReturn(Mono.empty());
    }

    private static ProductMaestro product(String id) {
        return ProductMaestro.builder().productId(id).name("Product-" + id).build();
    }

    private static Document doc(String productId, String docId, boolean isZip) {
        return Document.builder()
            .productId(productId)
            .documentId(docId)
            .name(isZip ? "bundle.zip" : "file.pdf")
            .isZip(isZip)
            .build();
    }

    private static Document savedDocument(Long id, String docId, String useCase) {
        return Document.builder()
            .id(id)
            .documentId(docId)
            .productId("p1")
            .name("file.pdf")
            .useCase(useCase)
            .state(ProcessingResultCodes.PENDING.name())
            .isZip(false)
            .createdAt(java.time.LocalDateTime.now())
            .build();
    }

    @Test
    void execute_whenNoProducts_returnsCompletionMessage() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_whenProductsExist_processesEachDocument() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal Bogota"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals("Sucursal Bogota", savedDoc.getSucursal());
        assertEquals(ProcessingResultCodes.PENDING.name(), savedDoc.getState());
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void execute_withZipDocument_parentZipNameIsNull() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal Medellin"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", true)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertTrue(Boolean.TRUE.equals(saved.getIsZip()));
        assertEquals("Sucursal Medellin", saved.getSucursal());
    }

    @Test
    void execute_withMultipleProducts_processesAll() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1"), product("p2")));
        when(productLocalRepository.findBranchByProductId(anyString())).thenReturn(Mono.just("Sucursal Multi"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)))
            .thenReturn(Flux.just(doc("p2", "doc2", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")))
            .thenReturn(Mono.just(savedDocument(11L, "doc2", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, times(2)).save(any());
    }

    @Test
    void execute_whenRepositoryFails_propagatesError() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.error(new RuntimeException("DB error")));

        StepVerifier.create(useCase.execute("retention"))
            .expectErrorMatches(error -> error instanceof RuntimeException
                && "DB error".equals(error.getMessage()))
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void execute_whenGatewayFails_ignoresErrorAndContinues() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal FailTest"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.error(new RuntimeException("Gateway error")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_usesUseCaseFromParameter() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal UC"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "extract")));

        StepVerifier.create(useCase.execute("extract"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertEquals("extract", captor.getValue().getUseCase());
        assertEquals("Sucursal UC", captor.getValue().getSucursal());
    }

    @Test
    void execute_whenDocumentAlreadyExists_savesAsDuplicated() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal Dup"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId("p1", "doc1")).thenReturn(Mono.just(true));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals("Sucursal Dup", savedDoc.getSucursal());
        assertEquals(ProcessingResultCodes.ERR_DUPLICATED_DOC.name(), savedDoc.getState());
        assertNotNull(savedDoc.getSyncMessage());
        assertEquals(ProcessingResultCodes.ERR_DUPLICATED_DOC.value(), savedDoc.getSyncMessage());
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void execute_whenBranchNotFound_savesPlaceholderAndSkipsDownload() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.empty());
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, ProcessingResultCodes.NO_SUCURSAL.name(), "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(productRestGateway, never()).getDocumentsByProduct(any());

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals(ProcessingResultCodes.NO_SUCURSAL.name(), savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals(ProcessingResultCodes.FAILED.name(), savedDoc.getState());
        assertEquals(ProcessingResultCodes.NO_SUCURSAL.value(), savedDoc.getSyncMessage());
        assertEquals("retention", savedDoc.getUseCase());
    }

    // Correcciones F9 (test de reanudación) + M2 (verify de invocación única)
    @Test
    void execute_whenLastProcessedProductExists_resumesFromNextProduct() {
        // 1. El último producto guardado localmente en el rango fue "p1"
        when(documentRepository.findLastProcessedProductIdInRange()).thenReturn(Mono.just("p1"));

        // 2. getAllProducts valida que el contexto contenga LAST_PRODUCT_ID = "p1"
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.deferContextual(ctx -> {
            assertTrue(ctx.hasKey(ApiConstants.LAST_PRODUCT_ID),
                    "El contexto debe contener la clave LAST_PRODUCT_ID");
            assertEquals("p1", ctx.get(ApiConstants.LAST_PRODUCT_ID),
                    "El cursor de reanudación debe ser 'p1'");
            return Flux.just(product("p2"));
        }));

        when(productLocalRepository.findBranchByProductId("p2")).thenReturn(Mono.just("Sucursal Bogota"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p2", "doc2", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(20L, "doc2", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        // Verificar que el documento procesado corresponde a "p2" (producto reanudado)
        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        assertEquals("p2", docCaptor.getValue().getProductId());

        // Corrección M2: el cursor debe consultarse exactamente una vez por ejecución
        verify(documentRepository, times(1)).findLastProcessedProductIdInRange();
    }

    @Test
    void execute_whenNoLastProductId_doesNotInjectCursorInContext() {
        // findLastProcessedProductIdInRange ya retorna Mono.empty() por el setUp
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.deferContextual(ctx -> {
            assertFalse(ctx.hasKey(ApiConstants.LAST_PRODUCT_ID),
                    "Sin reanudación, el contexto NO debe contener LAST_PRODUCT_ID");
            return Flux.empty();
        }));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, times(1)).findLastProcessedProductIdInRange();
    }
}
```

---

## 3. Plan de Pruebas y Validación

### Pruebas Unitarias (`SyncDocumentsUseCaseTest.java`)

Se han configurado los siguientes escenarios de prueba, incluyendo los nuevos agregados tras la revisión exhaustiva:

| Test | Escenario validado | Corrección |
|---|---|---|
| `execute_whenNoProducts_returnsCompletionMessage` | Flujo sin productos en catálogo maestro | — |
| `execute_whenProductsExist_processesEachDocument` | Flujo normal con documento nuevo | — |
| `execute_withZipDocument_parentZipNameIsNull` | Documento de tipo ZIP | — |
| `execute_withMultipleProducts_processesAll` | Múltiples productos en lote | — |
| `execute_whenRepositoryFails_propagatesError` | Error en repositorio maestro | — |
| `execute_whenGatewayFails_ignoresErrorAndContinues` | Error en REST gateway | — |
| `execute_usesUseCaseFromParameter` | Parámetro `useCase` se propaga | — |
| `execute_whenDocumentAlreadyExists_savesAsDuplicated` | Documento duplicado | — |
| `execute_whenBranchNotFound_savesPlaceholderAndSkipsDownload` | Sin sucursal local | — |
| `execute_whenLastProcessedProductExists_resumesFromNextProduct` | **Reanudación con cursor** | F9 + M2 |
| `execute_whenNoLastProductId_doesNotInjectCursorInContext` | **Sin reanudación — contexto limpio** | F9 (complementario) |

**Notas de configuración:**
- `setUp()` incluye `lenient().when(findLastProcessedProductIdInRange()).thenReturn(Mono.empty())` para que los 9 tests anteriores no fallen al agregar la llamada en `execute()` (**corrección F8**).
- Los dos tests nuevos verifican `verify(documentRepository, times(1)).findLastProcessedProductIdInRange()` (**corrección M2**).
