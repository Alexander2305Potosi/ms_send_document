# Plan de Implementación V2: Consulta de Estado de Procesos Asíncronos
## (Plan Corregido — Resuelve los 14 Hallazgos del Análisis)

Este plan corrige todos los errores críticos, mejoras y cobertura de pruebas identificados en el plan anterior `PLAN_ESTADO_PROCESOS.md`.

---

## Hallazgos Resueltos

| # | Severidad | Corrección Aplicada |
|---|-----------|---------------------|
| 1 | 🔴 CRÍTICO | Constructor de `ProductHandler` conserva los 3 campos existentes y agrega los 2 nuevos |
| 2 | 🔴 CRÍTICO | `ProductRoutes` mantiene el patrón `nest()` + `PathProperties` y agrega 2 beans adicionales |
| 3 | 🔴 CRÍTICO | `StateCount` en `domain.entity.product` (relación correcta: infra importa de dominio) |
| 4 | 🔴 CRÍTICO | Prefijo cambiado a `app.status.sync` y `app.status.process` para evitar colisión con `app.sync.sucursal-query` |
| 5 | 🟡 MEJORA | `ProductMasterRepository` agrega `Mono<Long> countAllProducts()` — evita cargar todos los registros |
| 6 | 🟡 MEJORA | `LocalTime.parse()` dentro de `Mono.fromCallable()` para propagar errores reactivamente |
| 7 | 🟡 MEJORA | Se elimina el check explícito de `ERR_DUPLICATED_DOC` en el usecase, ya que `isBusinessRule()` lo cubre |
| 8 | 🔴 CRÍTICO | `DomainConfig` registra `GetSyncStatusUseCase` y `GetProcessStatusUseCase` como `@Bean` |
| 9 | 🔴 CRÍTICO | `DomainConfig` añade `@EnableConfigurationProperties` para `SyncStatusProperties` y `ProcessStatusProperties` |
| 10 | 🟢 TEST | Se agrega prueba: `sync_whenLocalEqualsOrExceedsMaster_returnsOne` |
| 11 | 🟢 TEST | Se agrega prueba: `sync_whenLocalExceedsMaster_returnsOne` |
| 12 | 🟢 TEST | Se agrega prueba: `process_whenNoDocumentsToday_returnsOne` |
| 13 | 🟢 TEST | Se agrega prueba: `process_whenExceededDeadlineWithPending_returnsError` |
| 14 | 🟢 TEST | `ProductHandlerStatusTest` corrige el mock de headers para evitar NPE |

---

## 1. Diseño de la Solución

El cuerpo de la respuesta de ambos endpoints retornará únicamente:
- **En ejecución / completado con éxito:** `"1"`
- **Fallo técnico o tiempo límite excedido:** `"error consulta carga"`

### Propiedades Corregidas (sin colisión con `app.sync.sucursal-query`)
```yaml
app:
  status:
    sync:
      start-time: "08:00:00"    # Hora de inicio programada para la sincronización
      timeout-hours: 1          # Tiempo límite de la sincronización (en horas)
    process:
      start-time: "09:00:00"    # Hora de inicio programada para el procesamiento
      timeout-hours: 2          # Tiempo límite del procesamiento (en horas)
```

---

## 2. Clases a Modificar y Crear

### A. Capa de Configuración (Propiedades)

#### [NEW] `SyncStatusProperties.java`
> Prefijo `app.status.sync` para no colisionar con `app.sync.sucursal-query` existente.

```java
package com.example.fileprocessor.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.status.sync")
public record SyncStatusProperties(
    @NotBlank String startTime,
    @Min(1) int timeoutHours
) {}
```

#### [NEW] `ProcessStatusProperties.java`
```java
package com.example.fileprocessor.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.status.process")
public record ProcessStatusProperties(
    @NotBlank String startTime,
    @Min(1) int timeoutHours
) {}
```

#### [MODIFY] [ApiConstants.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java)
> Agregar las dos constantes de status al final de la clase.

```java
// Respuestas de estado del proceso
public static final String STATUS_OK = "1";
public static final String STATUS_ERROR = "error consulta carga";
```

#### [MODIFY] [application.yml](file:///Users/alexander2305/Downloads/file-processor-service/src/main/resources/application.yml)
> Agregar la sección `app.status` al final del archivo, sin tocar `app.sync` existente.

```yaml
  status:
    sync:
      start-time: "08:00:00"
      timeout-hours: 1
    process:
      start-time: "09:00:00"
      timeout-hours: 2
```

---

### B. Capa de Persistencia

#### [MODIFY] [ProductMasterRepository.java (Puerto)](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/port/out/ProductMasterRepository.java)
> Agregar método eficiente `countAllProducts()` que evita traer todos los registros a memoria.

```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductMasterRepository {
    Flux<ProductMaestro> getAllProducts();

    /**
     * Cuenta el total de productos maestros sin cargarlos todos en memoria.
     */
    Mono<Long> countAllProducts();
}
```

#### [MODIFY] [ProductMasterR2dbcAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/masterdb/ProductMasterR2dbcAdapter.java)
> Implementar `countAllProducts()` con consulta SQL directa `COUNT(*)`.

```java
@Override
public Mono<Long> countAllProducts() {
    return masterDatabaseClient.sql("SELECT COUNT(*) FROM productos_maestros")
            .map((row, metadata) -> row.get(0, Long.class))
            .one()
            .defaultIfEmpty(0L);
}
```

#### [NEW] `StateCount.java`
> Ubicado en dominio (infra importa de dominio — relación correcta).

```java
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
> Agregar los dos nuevos métodos de consulta.

```java
import com.example.fileprocessor.domain.entity.product.StateCount;

// Cuenta documentos creados hoy filtrados por caso de uso (para sync status)
Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

// Agrupa documentos por estado hoy filtrados por caso de uso (para process status)
Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);
```

#### [MODIFY] [DocumentRepository.java (R2DBC)](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentRepository.java)
> Agregar las queries con `@Query`. `StateCount` se importa desde el paquete de dominio.

```java
import com.example.fileprocessor.domain.entity.product.StateCount;

@Query("SELECT COUNT(*) FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2")
Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

@Query("SELECT estado_sincronizacion AS state, COUNT(*) AS total FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2 GROUP BY estado_sincronizacion")
Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);
```

#### [MODIFY] [DocumentR2dbcAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentR2dbcAdapter.java)
> Implementar los dos nuevos métodos del puerto delegando al repositorio R2DBC.

```java
@Override
public Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase) {
    return repository.countDocumentsCreatedToday(startOfDay, useCase);
}

@Override
public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase) {
    return repository.countDocumentsGroupedByStateToday(startOfDay, useCase);
}
```

---

### C. Capa de Dominio (Casos de Uso)

#### [NEW] `GetSyncStatusUseCase.java`
> Usa `countAllProducts()` en vez de `getAllProducts().count()`. Parseo de `LocalTime` dentro de `Mono.fromCallable()` para manejo reactivo de errores.

```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.config.SyncStatusProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetSyncStatusUseCase {

    private final ProductMasterRepository productMasterRepository;
    private final DocumentRepository documentRepository;
    private final SyncStatusProperties properties;

    public Mono<String> execute(String useCase, String traceId) {
        return Mono.fromCallable(() -> {
                    LocalTime startTime = LocalTime.parse(properties.startTime());
                    return startTime.plusHours(properties.timeoutHours());
                })
                .flatMap(deadlineTime -> {
                    LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
                    LocalTime now = LocalTime.now();

                    return Mono.zip(
                            productMasterRepository.countAllProducts().defaultIfEmpty(0L),
                            documentRepository.countDocumentsCreatedToday(startOfDay, useCase).defaultIfEmpty(0L)
                    ).map(tuple -> {
                        long masterCount = tuple.getT1();
                        long localCount = tuple.getT2();

                        if (localCount < masterCount && now.isAfter(deadlineTime)) {
                            return ApiConstants.STATUS_ERROR;
                        }
                        return ApiConstants.STATUS_OK;
                    });
                });
    }
}
```

#### [NEW] `GetProcessStatusUseCase.java`
> Elimina el check explícito de `ERR_DUPLICATED_DOC` (ya cubierto por `isBusinessRule()`). Parseo reactivo de propiedades.

```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.config.ProcessStatusProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetProcessStatusUseCase {

    private final DocumentRepository documentRepository;
    private final ProcessStatusProperties properties;

    public Mono<String> execute(String useCase, String traceId) {
        return Mono.fromCallable(() -> {
                    LocalTime startTime = LocalTime.parse(properties.startTime());
                    return startTime.plusHours(properties.timeoutHours());
                })
                .flatMap(deadlineTime -> {
                    LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
                    LocalTime now = LocalTime.now();

                    return documentRepository.countDocumentsGroupedByStateToday(startOfDay, useCase)
                            .collectList()
                            .map(list -> {
                                long processed = 0;
                                long pending = 0;
                                long technicalFailures = 0;

                                for (var row : list) {
                                    String state = row.getState();
                                    long count = row.getTotal();

                                    // isBusinessRule() ya incluye ERR_DUPLICATED_DOC
                                    if (ProcessingResultCodes.NO_SUCURSAL.name().equals(state)
                                            || ProcessingResultCodes.isBusinessRule(state)) {
                                        continue;
                                    }

                                    if (ProcessingResultCodes.PENDING.name().equals(state)
                                            || ProcessingResultCodes.IN_PROGRESS.name().equals(state)) {
                                        pending += count;
                                    } else if (ProcessingResultCodes.PROCESSED.name().equals(state)) {
                                        processed += count;
                                    } else {
                                        technicalFailures += count;
                                    }
                                }

                                long totalApplicable = processed + pending + technicalFailures;

                                if (totalApplicable == 0) {
                                    return ApiConstants.STATUS_OK;
                                }
                                if (pending > 0) {
                                    return now.isAfter(deadlineTime)
                                            ? ApiConstants.STATUS_ERROR
                                            : ApiConstants.STATUS_OK;
                                }
                                return (technicalFailures > 0)
                                        ? ApiConstants.STATUS_ERROR
                                        : ApiConstants.STATUS_OK;
                            });
                });
    }
}
```

---

### D. Capa de Aplicación (Configuración de Beans)

#### [MODIFY] [DomainConfig.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/application/service/config/DomainConfig.java)
> Registrar los dos nuevos use cases como `@Bean` y habilitar las nuevas propiedades.

```java
package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.*;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.domain.usecase.*;
import com.example.fileprocessor.infrastructure.config.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ProcessorsProperties.class,
        SyncStatusProperties.class,
        ProcessStatusProperties.class
})
public class DomainConfig {

    // ... beans existentes sin cambios (soapDocumentUseCase, s3DocumentUseCase, syncDocumentsUseCase) ...

    @Bean
    public GetSyncStatusUseCase getSyncStatusUseCase(
            ProductMasterRepository productMasterRepository,
            DocumentRepository documentRepository,
            SyncStatusProperties syncStatusProperties) {
        return new GetSyncStatusUseCase(productMasterRepository, documentRepository, syncStatusProperties);
    }

    @Bean
    public GetProcessStatusUseCase getProcessStatusUseCase(
            DocumentRepository documentRepository,
            ProcessStatusProperties processStatusProperties) {
        return new GetProcessStatusUseCase(documentRepository, processStatusProperties);
    }
}
```

---

### E. Capa de Presentación

#### [MODIFY] [ProductRoutes.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/ProductRoutes.java)
> Se mantiene el patrón `nest()` + `PathProperties` existente. Se agregan **2 beans nuevos** al lado de los existentes.

```java
package com.example.fileprocessor.infrastructure.entrypoints.rest;

import com.example.fileprocessor.infrastructure.entrypoints.rest.config.PathProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

@Configuration
@RequiredArgsConstructor
public class ProductRoutes {

    private final PathProperties pathProperties;

    // --- Beans existentes sin cambios ---

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

    // --- Nuevos beans para los endpoints de status ---

    @Bean
    public RouterFunction<ServerResponse> syncStatusRoute(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET("/products/sync/status/{type_job}"), handler::getSyncStatus)
        );
    }

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
> Se conservan los 3 campos existentes. Se agregan los 2 nuevos use cases al constructor.

```java
package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.*;
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

    // Campos existentes
    private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
    private final ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
    private final SyncDocumentsUseCase syncDocumentsUseCase;

    // Nuevos campos
    private final GetSyncStatusUseCase getSyncStatusUseCase;
    private final GetProcessStatusUseCase getProcessStatusUseCase;

    public ProductHandler(
            SoapDocumentProcessingUseCase soapDocumentUseCase,
            ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider,
            SyncDocumentsUseCase syncDocumentsUseCase,
            GetSyncStatusUseCase getSyncStatusUseCase,
            GetProcessStatusUseCase getProcessStatusUseCase) {
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
        this.syncDocumentsUseCase = syncDocumentsUseCase;
        this.getSyncStatusUseCase = getSyncStatusUseCase;
        this.getProcessStatusUseCase = getProcessStatusUseCase;
    }

    // --- Métodos existentes sin cambios (processPendingProducts, syncProducts, getProcessor) ---

    // --- Nuevos métodos ---

    public Mono<ServerResponse> getSyncStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(TYPE_JOB);

        LOGGER.log(Level.INFO, "getSyncStatus called - traceId: {0}, useCase: {1}",
                new Object[]{traceId, useCase});

        return getSyncStatusUseCase.execute(useCase, traceId)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }

    public Mono<ServerResponse> getProcessStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(TYPE_JOB);

        LOGGER.log(Level.INFO, "getProcessStatus called - traceId: {0}, useCase: {1}",
                new Object[]{traceId, useCase});

        return getProcessStatusUseCase.execute(useCase, traceId)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }
}
```

---

## 3. Pruebas Unitarias (Cobertura Completa)

### `GetSyncStatusUseCaseTest.java`
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.config.SyncStatusProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSyncStatusUseCaseTest {

    @Mock private ProductMasterRepository productMasterRepository;
    @Mock private DocumentRepository documentRepository;

    private GetSyncStatusUseCase buildUseCase(String startTime, int timeoutHours) {
        return new GetSyncStatusUseCase(productMasterRepository, documentRepository,
                new SyncStatusProperties(startTime, timeoutHours));
    }

    private String futureStartTime() {
        return LocalTime.now().minusHours(1).toString().substring(0, 8);
    }

    private String pastStartTime() {
        return LocalTime.now().minusHours(3).toString().substring(0, 8);
    }

    // ✅ Caso 1: dentro del deadline, local < master → STATUS_OK (aún en ejecución)
    @Test
    void execute_whenRunningWithinDeadline_returnsOne() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(5L));

        StepVerifier.create(buildUseCase(futureStartTime(), 3).execute("retention", "t1"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ Caso 2: excedió deadline con local < master → STATUS_ERROR
    @Test
    void execute_whenExceededDeadlineAndNotComplete_returnsError() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(5L));

        StepVerifier.create(buildUseCase(pastStartTime(), 1).execute("retention", "t2"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ Caso 3: local == master → STATUS_OK (completado)
    @Test
    void execute_whenLocalEqualsMaster_returnsOne() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(10L));

        StepVerifier.create(buildUseCase(pastStartTime(), 1).execute("retention", "t3"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ Caso 4: local > master → STATUS_OK
    @Test
    void execute_whenLocalExceedsMaster_returnsOne() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(5L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(8L));

        StepVerifier.create(buildUseCase(pastStartTime(), 1).execute("retention", "t4"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }
}
```

### `GetProcessStatusUseCaseTest.java`
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.config.ProcessStatusProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProcessStatusUseCaseTest {

    @Mock private DocumentRepository documentRepository;

    private GetProcessStatusUseCase buildUseCase(String startTime, int timeoutHours) {
        return new GetProcessStatusUseCase(documentRepository,
                new ProcessStatusProperties(startTime, timeoutHours));
    }

    private String futureDeadlineStart() {
        return LocalTime.now().minusHours(1).toString().substring(0, 8);
    }

    private String pastDeadlineStart() {
        return LocalTime.now().minusHours(3).toString().substring(0, 8);
    }

    private void mockCounts(List<StateCount> counts) {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), anyString()))
                .thenReturn(Flux.fromIterable(counts));
    }

    // ✅ Caso 1: sin documentos hoy → STATUS_OK
    @Test
    void execute_whenNoDocumentsToday_returnsOne() {
        mockCounts(List.of());

        StepVerifier.create(buildUseCase(futureDeadlineStart(), 2).execute("retention", "t1"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ Caso 2: pendientes dentro del deadline → STATUS_OK
    @Test
    void execute_whenPendingWithinDeadline_returnsOne() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PENDING.name(), 5L)));

        StepVerifier.create(buildUseCase(futureDeadlineStart(), 2).execute("retention", "t2"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ Caso 3: pendientes y excedió deadline → STATUS_ERROR
    @Test
    void execute_whenPendingAndExceededDeadline_returnsError() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PENDING.name(), 3L)));

        StepVerifier.create(buildUseCase(pastDeadlineStart(), 1).execute("retention", "t3"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ Caso 4: todos procesados, sin fallos → STATUS_OK
    @Test
    void execute_whenAllProcessedNoFailures_returnsOne() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PROCESSED.name(), 10L)));

        StepVerifier.create(buildUseCase(pastDeadlineStart(), 1).execute("retention", "t4"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }

    // ✅ Caso 5: fallos técnicos sin pendientes → STATUS_ERROR
    @Test
    void execute_whenHasTechnicalFailures_returnsError() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.PROCESSED.name(), 8L),
                new StateCount(ProcessingResultCodes.FAILED.name(), 2L)
        ));

        StepVerifier.create(buildUseCase(futureDeadlineStart(), 2).execute("retention", "t5"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .verifyComplete();
    }

    // ✅ Caso 6: solo estados excluidos (ERR_DUPLICATED_DOC, NO_SUCURSAL) → STATUS_OK
    @Test
    void execute_whenOnlyExcludedStates_returnsOne() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.ERR_DUPLICATED_DOC.name(), 5L),
                new StateCount(ProcessingResultCodes.NO_SUCURSAL.name(), 3L)
        ));

        StepVerifier.create(buildUseCase(pastDeadlineStart(), 1).execute("retention", "t6"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .verifyComplete();
    }
}
```

### `ProductHandlerStatusTest.java`
```java
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
        // Constructor con los 5 parámetros reales (existentes + nuevos)
        handler = new ProductHandler(null, null, null, getSyncStatusUseCase, getProcessStatusUseCase);
    }

    private void mockRequestHeaders() {
        // Fix hallazgo #14: el mock de headers debe retornar un HttpHeaders válido
        ServerRequest.Headers headers = Mockito.mock(ServerRequest.Headers.class);
        when(headers.asHttpHeaders()).thenReturn(new HttpHeaders());
        when(serverRequest.headers()).thenReturn(headers);
    }

    // ✅ getSyncStatus retorna HTTP 200 con STATUS_OK
    @Test
    void getSyncStatus_whenUseCaseReturnsOk_responds200() {
        mockRequestHeaders();
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getSyncStatusUseCase.execute(anyString(), anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_OK));

        StepVerifier.create(handler.getSyncStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }

    // ✅ getProcessStatus retorna HTTP 200 con STATUS_OK
    @Test
    void getProcessStatus_whenUseCaseReturnsOk_responds200() {
        mockRequestHeaders();
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getProcessStatusUseCase.execute(anyString(), anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_OK));

        StepVerifier.create(handler.getProcessStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }

    // ✅ getSyncStatus retorna HTTP 200 con STATUS_ERROR (el status va en el body, no en el código HTTP)
    @Test
    void getSyncStatus_whenUseCaseReturnsError_stillResponds200() {
        mockRequestHeaders();
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getSyncStatusUseCase.execute(anyString(), anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_ERROR));

        StepVerifier.create(handler.getSyncStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }
}
```
