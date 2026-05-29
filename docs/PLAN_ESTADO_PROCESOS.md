# Plan de Implementación Simplificado: Consulta de Estado de Procesos Asíncronos

Este plan describe la propuesta simplificada para consultar el progreso y estado de los procesos asíncronos consultando directamente la base de datos de manera independiente por cada endpoint, siguiendo la arquitectura: **Handler -> UseCase**, incorporando trazabilidad con `message-id`, y aplicando las reglas configurables de tiempo de inicio y espera independientes para cada uno.

---

## 1. Diseño de la Solución (Estados Únicos: "1" o "error consulta carga")

El cuerpo de la respuesta de ambos endpoints únicamente retornará un string directo:
* **En ejecución o completado con éxito:** `"1"`.
* **Fallo por error técnico o por tiempo límite excedido:** `"error consulta carga"`.

### Propiedades de Configuración Independientes (Tiempo Fijo de Inicio y Timeout)
Definiremos propiedades separadas para cada proceso en `application.yml`:
```yaml
app:
  sync:
    start-time: "08:00:00"   # Hora de inicio programada para la sincronización
    timeout-hours: 1        # Tiempo límite de la sincronización (en horas)
  process:
    start-time: "09:00:00"   # Hora de inicio programada para el procesamiento (envío SOAP/S3)
    timeout-hours: 2        # Tiempo límite del procesamiento (en horas)
```

* **`GetSyncStatusUseCase`** utilizará las propiedades independientes bajo `app.sync`.
* **`GetProcessStatusUseCase`** utilizará las propiedades independientes bajo `app.process`.

---

### A. Sincronización (`GET /api/v1/products/sync/status/{type_job}`)
1. **Total Maestro:** `productMasterRepository.getAllProducts().count()` (Reutilizado).
2. **Total Local:** `documentRepository.countDocumentsCreatedToday(startOfDay, useCase)` (Filtrado por el caso de uso del path).

**Reglas de Decisión del Estado:**
* Si `localInsertedToday >= masterProductsToday` $\rightarrow$ `ApiConstants.STATUS_OK` (`"1"`).
* Si `localInsertedToday < masterProductsToday`:
  * Evaluamos si la hora actual ya superó el tiempo límite de sincronización (`LocalTime.now().isAfter(syncStartTime.plusHours(syncTimeoutHours))`):
    * **Sí (Excedió el tiempo límite y no completó):** Retorna `ApiConstants.STATUS_ERROR` (`"error consulta carga"`).
    * **No (Sigue dentro del tiempo esperado de ejecución):** Retorna `ApiConstants.STATUS_OK` (`"1"`).

---

### B. Procesamiento (`GET /api/v1/products/process/status/{type_job}`)
Este endpoint **no valida cantidades** contra la base maestra. Determina el progreso del procesamiento real del día de hoy consultando e interpretando los estados de los documentos locales de hoy.

1. **Caso de Uso:** `GetProcessStatusUseCase` consulta **únicamente** los totales agrupados del día de hoy filtrados por el caso de uso (`useCase`).
   * **Excluidos:** `ERR_DUPLICATED_DOC`, `NO_SUCURSAL` y los marcados como `ProcessingResultCodes.isBusinessRule(state)`.
   * **Pendientes o En Progreso:** `PENDING`, `IN_PROGRESS`.
   * **Exitosos:** `PROCESSED`.
   * **Fallidos Técnicos:** `FAILED` (u otros estados de error técnicos similares).

**Reglas de Decisión del Estado:**
* Si `totalApplicable == 0` $\rightarrow$ `ApiConstants.STATUS_OK` (`"1"`).
* Si `pendingOrInProgress > 0`:
  * Evaluamos si la hora actual ya superó el tiempo límite programado de procesamiento (`LocalTime.now().isAfter(processStartTime.plusHours(processTimeoutHours))`):
    * **Sí (Excedió el tiempo límite y quedan documentos pendientes):** Retorna `ApiConstants.STATUS_ERROR` (`"error consulta carga"`).
    * **No (Sigue procesándose en el tiempo esperado):** Retorna `ApiConstants.STATUS_OK` (`"1"`).
* Si `pendingOrInProgress == 0` (Ya no hay pendientes):
  * Si `failedTechnicalReasons > 0` $\rightarrow$ `ApiConstants.STATUS_ERROR` (`"error consulta carga"`).
  * De lo contrario $\rightarrow$ `ApiConstants.STATUS_OK` (`"1"`).

---

## 2. Clases a Modificar y Crear (Estructura Handler -> UseCase)

### A. Capa de Infraestructura (Constantes y Base de Datos)

#### [MODIFY] [ApiConstants.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java)
```java
package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

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
    public static final String STATUS_OK = "1";
    public static final String STATUS_ERROR = "error consulta carga";
}
```

#### [NEW] `SyncProperties.java`
```java
package com.example.fileprocessor.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    @NotBlank String startTime,
    @Min(1) int timeoutHours
) {}
```

#### [NEW] `ProcessProperties.java`
```java
package com.example.fileprocessor.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.process")
public record ProcessProperties(
    @NotBlank String startTime,
    @Min(1) int timeoutHours
) {}
```

#### [MODIFY] [DocumentRepository.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentRepository.java)
```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

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

    // Cuenta los documentos insertados hoy filtrando por caso de uso (Usado por Sync status)
    @Query("SELECT COUNT(*) FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2")
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    // Agrupa por estado y cuenta hoy filtrando por caso de uso (Usado por Process status)
    @Query("SELECT estado_sincronizacion AS state, COUNT(*) AS total FROM documentos WHERE fecha_carga >= $1 AND caso_uso = $2 GROUP BY estado_sincronizacion")
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);
}
```

#### [NEW] `StateCount.java`
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

---

### B. Capa de Dominio (Casos de Uso)

#### [NEW] `GetSyncStatusUseCase.java`
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.config.SyncProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetSyncStatusUseCase {

    private final ProductMasterRepository productMasterRepository;
    private final DocumentRepository documentRepository;
    private final SyncProperties properties;

    public Mono<String> execute(String useCase, String traceId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        LocalTime startTime = LocalTime.parse(properties.startTime());
        LocalTime deadlineTime = startTime.plusHours(properties.timeoutHours());
        LocalTime now = LocalTime.now();

        return Mono.zip(
            productMasterRepository.getAllProducts().count().defaultIfEmpty(0L),
            documentRepository.countDocumentsCreatedToday(startOfDay, useCase).defaultIfEmpty(0L)
        ).map(tuple -> {
            long masterCount = tuple.getT1();
            long localCount = tuple.getT2();

            if (localCount < masterCount && now.isAfter(deadlineTime)) {
                return ApiConstants.STATUS_ERROR;
            }
            return ApiConstants.STATUS_OK;
        });
    }
}
```

#### [NEW] `GetProcessStatusUseCase.java`
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.config.ProcessProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetProcessStatusUseCase {

    private final DocumentRepository documentRepository;
    private final ProcessProperties properties;

    public Mono<String> execute(String useCase, String traceId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        LocalTime startTime = LocalTime.parse(properties.startTime());
        LocalTime deadlineTime = startTime.plusHours(properties.timeoutHours());
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

                        if (ProcessingResultCodes.NO_SUCURSAL.name().equals(state) || 
                            ProcessingResultCodes.isBusinessRule(state)) {
                            continue;
                        }

                        if (ProcessingResultCodes.PENDING.name().equals(state) || 
                            ProcessingResultCodes.IN_PROGRESS.name().equals(state)) {
                            pending += count;
                        } else if (ProcessingResultCodes.PROCESSED.name().equals(state)) {
                            processed += count;
                        } else {
                            technicalFailures += count;
                        }
                    }

                    long totalApplicable = processed + pending + technicalFailures;
                    
                    if (totalApplicable > 0) {
                        if (pending > 0) {
                            return now.isAfter(deadlineTime) ? ApiConstants.STATUS_ERROR : ApiConstants.STATUS_OK;
                        } else {
                            return (technicalFailures > 0) ? ApiConstants.STATUS_ERROR : ApiConstants.STATUS_OK;
                        }
                    }
                    return ApiConstants.STATUS_OK;
                });
    }
}
```

---

### C. Capa de Presentación (Rutas y Controladores)

#### [MODIFY] [ProductRoutes.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/ProductRoutes.java)
```java
package com.example.fileprocessor.infrastructure.entrypoints.rest;

import com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ProductRoutes {

    @Bean
    public RouterFunction<ServerResponse> productRouter(ProductHandler productHandler) {
        return RouterFunctions.route()
                .POST("/api/v1/products/sync", productHandler::syncProducts)
                .GET("/api/v1/products/sync/status/{type_job}", productHandler::getSyncStatus)
                .GET("/api/v1/products", productHandler::processPendingDocuments)
                .GET("/api/v1/products/process/status/{type_job}", productHandler::getProcessStatus)
                .build();
    }
}
```

#### [MODIFY] [ProductHandler.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java)
```java
package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.GetProcessStatusUseCase;
import com.example.fileprocessor.domain.usecase.GetSyncStatusUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class ProductHandler {

    private final GetSyncStatusUseCase getSyncStatusUseCase;
    private final GetProcessStatusUseCase getProcessStatusUseCase;

    public ProductHandler(GetSyncStatusUseCase getSyncStatusUseCase, GetProcessStatusUseCase getProcessStatusUseCase) {
        this.getSyncStatusUseCase = getSyncStatusUseCase;
        this.getProcessStatusUseCase = getProcessStatusUseCase;
    }

    public Mono<ServerResponse> getSyncStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(ApiConstants.HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(ApiConstants.TYPE_JOB);

        return getSyncStatusUseCase.execute(useCase, traceId)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }

    public Mono<ServerResponse> getProcessStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(ApiConstants.HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(ApiConstants.TYPE_JOB);

        return getProcessStatusUseCase.execute(useCase, traceId)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }
}
```

---

## 3. Pruebas Unitarias para Validar la Lógica

#### [NEW] [GetSyncStatusUseCaseTest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/test/java/com/example/fileprocessor/domain/usecase/GetSyncStatusUseCaseTest.java)
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.config.SyncProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
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

    @Mock
    private ProductMasterRepository productMasterRepository;

    @Mock
    private DocumentRepository documentRepository;

    private GetSyncStatusUseCase useCase;

    @Test
    void execute_whenRunningWithinTimeRange_returnsOne() {
        LocalTime futureDeadline = LocalTime.now().plusHours(1);
        String startTimeStr = futureDeadline.minusHours(1).toString().substring(0, 8);
        SyncProperties properties = new SyncProperties(startTimeStr, 2);
        
        useCase = new GetSyncStatusUseCase(productMasterRepository, documentRepository, properties);

        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(new Object(), new Object()));
        when(documentRepository.countDocumentsCreatedToday(any(LocalDateTime.class), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute("retention", "trace-123"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .expectComplete()
                .verify();
    }

    @Test
    void execute_whenExceededTimeRange_returnsErrorString() {
        LocalTime pastStartTime = LocalTime.now().minusHours(2);
        String startTimeStr = pastStartTime.toString().substring(0, 8);
        SyncProperties properties = new SyncProperties(startTimeStr, 1);
        
        useCase = new GetSyncStatusUseCase(productMasterRepository, documentRepository, properties);

        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(new Object(), new Object()));
        when(documentRepository.countDocumentsCreatedToday(any(LocalDateTime.class), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute("retention", "trace-123"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .expectComplete()
                .verify();
    }
}
```

#### [NEW] [GetProcessStatusUseCaseTest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/test/java/com/example/fileprocessor/domain/usecase/GetProcessStatusUseCaseTest.java)
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.config.ProcessProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.domain.entity.product.StateCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProcessStatusUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    private GetProcessStatusUseCase useCase;

    @Test
    void execute_whenRunningWithinTimeRange_returnsOne() {
        LocalTime futureDeadline = LocalTime.now().plusHours(1);
        String startTimeStr = futureDeadline.minusHours(1).toString().substring(0, 8);
        ProcessProperties properties = new ProcessProperties(startTimeStr, 2);
        
        useCase = new GetProcessStatusUseCase(documentRepository, properties);

        List<StateCount> counts = List.of(
                new StateCount(ProcessingResultCodes.PENDING.name(), 1L)
        );
        when(documentRepository.countDocumentsGroupedByStateToday(any(LocalDateTime.class), anyString()))
                .thenReturn(Flux.fromIterable(counts));

        StepVerifier.create(useCase.execute("retention", "trace-123"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_OK, res))
                .expectComplete()
                .verify();
    }

    @Test
    void execute_whenHasTechnicalFailures_returnsErrorString() {
        LocalTime futureDeadline = LocalTime.now().plusHours(1);
        String startTimeStr = futureDeadline.minusHours(1).toString().substring(0, 8);
        ProcessProperties properties = new ProcessProperties(startTimeStr, 2);
        
        useCase = new GetProcessStatusUseCase(documentRepository, properties);
        
        List<StateCount> counts = List.of(
                new StateCount(ProcessingResultCodes.PROCESSED.name(), 1L),
                new StateCount(ProcessingResultCodes.FAILED.name(), 1L)
        );
        when(documentRepository.countDocumentsGroupedByStateToday(any(LocalDateTime.class), anyString()))
                .thenReturn(Flux.fromIterable(counts));

        StepVerifier.create(useCase.execute("retention", "trace-123"))
                .assertNext(res -> assertEquals(ApiConstants.STATUS_ERROR, res))
                .expectComplete()
                .verify();
    }
}
```

#### [NEW] [ProductHandlerStatusTest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/test/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandlerStatusTest.java)
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

    @Mock
    private GetSyncStatusUseCase getSyncStatusUseCase;

    @Mock
    private GetProcessStatusUseCase getProcessStatusUseCase;

    @Mock
    private ServerRequest serverRequest;

    private ProductHandler productHandler;

    @BeforeEach
    void setUp() {
        productHandler = new ProductHandler(null, null, null, getSyncStatusUseCase, getProcessStatusUseCase);
    }

    @Test
    void getSyncStatus_returnsOk() {
        ServerRequest.Headers headers = Mockito.mock(ServerRequest.Headers.class);
        when(serverRequest.headers()).thenReturn(headers);
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");

        when(getSyncStatusUseCase.execute(anyString(), anyString())).thenReturn(Mono.just(ApiConstants.STATUS_OK));

        Mono<ServerResponse> responseMono = productHandler.getSyncStatus(serverRequest);

        StepVerifier.create(responseMono)
                .assertNext(response -> assertEquals(HttpStatus.OK, response.statusCode()))
                .expectComplete()
                .verify();
    }

    @Test
    void getProcessStatus_returnsOk() {
        ServerRequest.Headers headers = Mockito.mock(ServerRequest.Headers.class);
        when(serverRequest.headers()).thenReturn(headers);
        when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");

        when(getProcessStatusUseCase.execute(anyString(), anyString())).thenReturn(Mono.just(ApiConstants.STATUS_OK));

        Mono<ServerResponse> responseMono = productHandler.getProcessStatus(serverRequest);

        StepVerifier.create(responseMono)
                .assertNext(response -> assertEquals(HttpStatus.OK, response.statusCode()))
                .expectComplete()
                .verify();
    }
}
```
