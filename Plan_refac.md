# Plan Conjunto de Mejora: file-processor-service

> Rama: `feature/v2.0` | Fecha: 2026-04-27
>
> Revisores: Junior Backend (2 anos exp.) · Experto Backend (15 anos exp.)
>
> Metodo: Analisis independiente en paralelo + socializacion de hallazgos + consolidacion conjunta

---

## Resumen Ejecutivo

Ambos revisores analizaron exhaustivamente los ~70 archivos Java del microservicio. El diagnostico es consistente: la arquitectura hexagonal esta **violada en la capa de dominio** (4 dependencias dominio→infraestructura), el **Circuit Breaker esta completamente bypassado** (los adaptadores convierten todos los errores en senales de exito con `onErrorResume`), existen **anti-patrones reactivos** (`subscribe()` anidado sin error consumer en `ProductHandler`), y aproximadamente **60% de los tests no prueban comportamiento real** (solo getters/constructores o existencia de clases por reflexion).

El plan resultante contiene **33 acciones** organizadas en **5 fases**, priorizadas por criticidad e impacto.

---

## 1. Comparacion de Hallazgos

### Donde AMBOS coincidieron (hallazgos independientes identicos)

| # | Hallazgo | Junior | Experto |
|---|----------|--------|---------|
| H1 | Dominio importa `ApiConstants` de infraestructura (4 archivos) | AV-1 | #1 |
| H2 | Circuit Breaker bypassado por `onErrorResume` en ambos gateways | BUG-5 | #10 |
| H3 | `subscribe()` anidado en `ProductHandler` sin error consumer | BUG-1, BUG-2 | #4, #5 |
| H4 | `FileUploadProperties` + `app.file.*` es configuracion muerta | DC-1 | #26 |
| H5 | Value objects `TraceId`/`DocumentId` definidos pero NUNCA usados | DC-2 | #14 |
| H6 | ~60% de tests prueban getters/constructores, no comportamiento | TQ-1, TQ-4 | #33 |
| H7 | Cero tests para `DocumentProcessingPipeline`, `Orchestrator`, repositorios | TQ-1, M1 | #34 |
| H8 | `soapDocumentSkipHandler` nombrado para SOAP pero usado en S3 tambien | NM-2 | #19 |
| H9 | `createProductDocument` ignora el parametro `traceId` | DC-6 | #31 |
| H10 | `DocumentSkipHandler` marca skipped como `success=true` | BUG-4 | #32 |

### Lo que el Experto vio y el Junior no

| # | Hallazgo | Riesgo |
|---|----------|--------|
| E1 | `DocumentProcessingPipeline` importa directamente `Resilience4j` (violacion hexagonal) | Critica |
| E2 | 5 de 6 puertos tienen UNA sola implementacion (YAGNI) | Alta |
| E3 | Cero metricas Micrometer (`@Timed`, `Counter`, `Timer`) | Alta |
| E4 | Patron de log sin `%X{traceId}` — imposible filtrar logs por trace | Alta |
| E5 | `application-prod.yml` usa `server.shutdown: immediate` — mata procesamiento en vuelo | Alta |
| E6 | Extraccion de error codes usa `String.contains()` fragile | Critica |
| E7 | `CommunicationException` no tiene constructor con `Throwable cause` | Alta |
| E8 | Sin `HealthIndicator` para dependencias externas (SOAP, REST API) | Media |
| E9 | `ZipArchive` es entidad de dominio haciendo trabajo de infraestructura (ZIP I/O) | Media |
| E10 | `ProductRestGatewayAdapter` sin timeout a pesar de tener `timeoutSeconds` en config | Media |

### Lo que el Junior vio y el Experto no priorizo

| # | Hallazgo | Riesgo |
|---|----------|--------|
| J1 | Condicion de carrera en contadores de progreso (`read-then-write` en `ProductHandler`) | Critica |
| J2 | `Base64Utils` existe pero NADIE lo usa — encoding/decoding duplicado en 4 lugares | Alta |
| J3 | `findPendingDocuments()` incluye documentos en estado `PROCESSING` (nombre mentiroso) | Alta |
| J4 | 8 archivos para trazar un documento de entrada a salida | Alta |
| J5 | Agregar un nuevo processor type requiere tocar 8 ubicaciones distintas | Alta |
| J6 | `DocumentRestProperties` tiene 3 campos nunca leidos (`listPath`, `getPath`, `timeoutSeconds`) | Media |
| J7 | Tests con `lenient()` generalizado — mascara falsos positivos | Media |
| J8 | `LoadProductsUseCase` pierde errores de extraccion ZIP (retorna `Flux.empty()` en vez de error) | Media |
| J9 | JAXBContext inicializado redundantemente en `SoapMapper` y `SoapEnvelopeWrapper` | Baja |
| J10 | Constantes duplicadas entre `AsyncOperationStatus` y `ApiConstants` | Baja |

---

## 2. Plan de Accion Unificado (33 acciones)

### FASE 1 — Correccion de Bugs Criticos y Violaciones de Arquitectura

#### P1 — [CRITICA] Eliminar imports de infraestructura en el dominio (4 archivos)

**Archivos:**
- `domain/entity/ProductDocumentInfo.java:3` — import `ApiConstants`
- `domain/entity/SoapResponse.java:3` — import `ApiConstants`
- `domain/usecase/DocumentValidationRules.java:3` — import `ApiConstants`
- `domain/usecase/FileValidator.java:6` — import `ApiConstants`

**Problema:** El dominio depende directamente de `infrastructure.entrypoints.rest.constants.ApiConstants`. Esto rompe la regla fundamental de Arquitectura Hexagonal: el dominio nunca debe depender de infraestructura.

**Solucion:**
- `EXTENSION_ZIP` ("zip") → mover a `ProductDocumentInfo` como constante privada o a un enum `DocumentType`
- `DEFAULT_FOLDER` (".") → hardcodear en `DocumentValidationRules` (es una convencion universal de filesystem)
- `PATH_DOUBLE_DOT`, `PATH_SLASH`, `PATH_BACKSLASH` → constantes privadas en `FileValidator`
- `SOAP_STATUS_OK` ("OK") → mover a `SoapResponse` como constante publica

**Beneficio:** El dominio se vuelve verdaderamente independiente. Cambios en infraestructura no rompen el dominio.

---

#### P2 — [CRITICA] Extraer dependencia de Resilience4j del dominio

**Archivo:** `domain/usecase/DocumentProcessingPipeline.java:9-11`

**Problema:** El caso de uso importa directamente `CircuitBreaker`, `CallNotPermittedException`, y `CircuitBreakerOperator` de Resilience4j. Esto acopla el dominio a una libreria de infraestructura especifica.

**Solucion:** Crear un puerto de dominio `ResilienceOperator` que abstraiga la operacion del Circuit Breaker:

```java
// domain/port/out/ResilienceOperator.java
@FunctionalInterface
public interface ResilienceOperator {
    <T> Mono<T> decorate(Mono<T> source, String operationName);
}
```

La implementacion con Resilience4j vive en `infrastructure.helpers.config.CircuitBreakerConfiguration`.

**Beneficio:** El dominio desacoplado de Resilience4j. Cambiar la libreria de resiliencia no toca el dominio.

---

#### P3 — [CRITICA] Arreglar Circuit Breaker: eliminar `onErrorResume` que lo anula

**Archivos:**
- `infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:97-129`
- `infrastructure/drivenadapters/aws/S3GatewayAdapter.java:67-74`

**Problema:** Ambos gateways usan `onErrorResume` que convierte TODOS los errores (timeout, conexion, 5xx) en `Mono.just(FileUploadResult)`. El `CircuitBreakerOperator` solo detecta fallos via senal `onError`. Al nunca recibir `onError`, el CB permanece CERRADO permanentemente. Es **codigo de resiliencia muerto**.

**Solucion:** Distinguir entre fallos de infraestructura (que DEBEN propagarse como error para que el CB reaccione) y fallos de negocio (que deben retornar como resultado):

```java
.onErrorResume(throwable -> {
    // Fallos de infraestructura → propagar error (CB reacciona)
    if (isInfrastructureFailure(throwable)) {
        return Mono.error(new CommunicationException(..., throwable));
    }
    // Fallos de negocio → resultado con status FAILURE
    return Mono.just(buildFailureResult(throwable));
})
```

**Beneficio:** El Circuit Breaker vuelve a funcionar. En produccion, si el servicio externo se cae, el CB se abre y evita llamadas innecesarias.

---

#### P4 — [CRITICA] Eliminar `subscribe()` anidado en ProductHandler.loadProducts()

**Archivo:** `infrastructure/entrypoints/rest/handler/ProductHandler.java:53-61`

**Problema:** `loadProductsUseCase.execute().subscribe()` se ejecuta dentro de `Mono.fromRunnable()`. Esta senal reactiva esta **completamente desacoplada** de Spring WebFlux. Errores no capturados por `doOnError` disparan `ErrorCallbackNotImplemented`. Si el request HTTP termina (timeout), el procesamiento continua huerfano.

**Solucion:** Retornar el `Mono` directamente a WebFlux:

```java
return loadProductsUseCase.execute()
    .doOnNext(result -> log.info(...))
    .doOnError(error -> log.error(...))
    .thenReturn(initialStatus);
```

**Beneficio:** Errores correctamente propagados a WebFlux. Sin suscripciones huerfanas.

---

#### P5 — [CRITICA] Arreglar `subscribe()` anidado en processPendingProducts

**Archivo:** `infrastructure/entrypoints/rest/handler/ProductHandler.java:95`

**Problema:** `.doOnComplete(() -> asyncOperationRepository.markCompleted(traceId).subscribe())` — si `markCompleted` falla, el error se pierde. Y `markCompleted` no esta encadenado al flujo principal.

**Solucion:** Encadenar con `.then()`:

```java
.then(asyncOperationRepository.markCompleted(traceId))
.then(asyncOperationRepository.findByTraceId(traceId))
.flatMap(status -> ServerResponse.accepted().bodyValue(status));
```

**Beneficio:** `markCompleted` participa en el flujo reactive. Si falla, el error se propaga.

---

#### P6 — [CRITICA] Reemplazar extraccion de error codes por substring con tipos explicitos

**Archivo:** `domain/usecase/DocumentProcessingPipeline.java:135-140`

**Problema:** `message.contains("timeout")` y `message.contains("validation")` es fragil. Si un mensaje de error externo contiene la palabra "timeout" como parte de un contexto de negocio, se clasifica incorrectamente.

**Solucion:** Usar el campo `errorCode` del `FileUploadResult` (que ya existe) en lugar de extraerlo del mensaje:

```java
private String extractErrorCode(FileUploadResult result) {
    return result.getErrorCode() != null ? result.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
}
```

Asegurar que los adaptadores siempre asignen `errorCode` al construir `FileUploadResult` en casos de error.

**Beneficio:** Clasificacion de errores determinista y tipada.

---

#### P7 — [CRITICA] Arreglar condicion de carrera en contadores de progreso

**Archivo:** `infrastructure/entrypoints/rest/handler/ProductHandler.java:86-93`

**Problema:** `findByTraceId(traceId)` lee un snapshot, luego `updateProgress(...)` escribe basado en ese snapshot. Con `maxConcurrency=10`, multiples flatMaps concurrentes leen el mismo valor, lo incrementan, y escriben — perdiendo actualizaciones.

**Solucion:** Mover la atomicidad al repositorio con operaciones atomicas:

```java
// En InMemoryAsyncOperationRepository
public Mono<Void> incrementProgress(String traceId, boolean success) {
    return Mono.fromRunnable(() -> {
        operations.compute(traceId, (key, op) -> {
            op.setProcessedItems(op.getProcessedItems() + 1);
            if (success) op.setSuccessItems(op.getSuccessItems() + 1);
            else op.setFailedItems(op.getFailedItems() + 1);
            return op;
        });
    });
}
```

**Beneficio:** Contadores correctos bajo carga concurrente.

---

### FASE 2 — Eliminacion de Codigo Muerto y Configuracion Zombie

#### P8 — [ALTA] Eliminar `FileUploadProperties` y `app.file.*` (4 archivos YAML)

**Archivos:**
- `infrastructure/helpers/config/FileUploadProperties.java` (eliminar)
- `application.yml` — seccion `app.file.*` (eliminar)
- `application-dev.yml` — seccion `app.file.*` (eliminar)
- `application-prod.yml` — seccion `app.file.*` (eliminar)
- `application-test.yml` — seccion `app.file.*` (eliminar)
- `Application.java:13` — remover `FileUploadProperties.class` de `@EnableConfigurationProperties`
- `TestConfig.java:11` — remover `FileUploadProperties.class` de `@EnableConfigurationProperties`

**Problema:** `FileUploadProperties` implementa `FileValidationConfig` pero NUNCA es inyectado en ningun bean. `DomainConfig` usa `ProcessorConfig.getSoap()` y `ProcessorConfig.getS3()` para validacion. El `app.file.*` en YAML es un espejismo — cambiarlo no tiene efecto.

**Beneficio:** -35 lineas de codigo, -20 lineas de YAML. Una fuente unica de verdad para config de validacion.

---

#### P9 — [ALTA] Eliminar value objects no usados o integrarlos

**Archivos:**
- `domain/valueobject/TraceId.java`
- `domain/valueobject/DocumentId.java`
- Tests asociados: `DocumentIdTest.java`, `TraceIdTest.java`

**Problema:** `DocumentId` y `TraceId` son value objects con validacion, pero absolutamente NINGUN codigo de produccion los usa. Todos los metodos reciben `String`. Esto es codigo "aspiracional" que nunca se adopto.

**Decision de arquitectura (socializado):** Ambos revisores acordaron **integrarlos progresivamente** en vez de eliminarlos, empezando por los puntos de entrada:

- `DocumentProcessingOrchestrator` acepta `TraceId` en lugar de `String`
- `FileGateway.send(DocumentSendRequest)` — el request ya incluye `String traceId`, cambiarlo a `TraceId`
- `ProductDocumentRepository.updateStatus` — cambiar parametro `String documentId` a `DocumentId`

Si el equipo decide no adoptarlos → eliminar.

**Beneficio:** Type safety. El compilador evita intercambiar `traceId` con `documentId`.

---

#### P10 — [ALTA] Eliminar tests que prueban solo getters/constructores/reflection

**Archivos a eliminar:**
- `ProductToProcessTest.java` — solo builder/getters
- `ProductDocumentToProcessTest.java` — test `getterAnnotations_shouldWork()` prueba Lombok
- `DocumentStatusTest.java` — prueba count de enum y `.name()`
- `MediaTypeConstantsTest.java` — prueba que constante == si misma
- `ProductRoutesTest.java` — `Class.forName()` no prueba comportamiento
- `WebFluxConfigTest.java` — mismo patron de reflection
- `SoapNamespacesTest.java` — strings constantes
- `DocumentRestPropertiesTest.java` — record constructor/getters
- `S3PropertiesTest.java` — record constructor/getters
- `FileUploadPropertiesTest.java` — record constructor/getters

**Problema:** Estos tests inflan metrica de cobertura dando falsa confianza. Probar que Lombok genera getters es probar Lombok, no la aplicacion.

**Beneficio:** Suite de tests mas ligera y honesta. Elimina ~300 lineas de tests sin valor.

---

#### P11 — [MEDIA] Eliminar constantes duplicadas entre `AsyncOperationStatus` y `ApiConstants`

**Archivos:**
- `domain/entity/AsyncOperationStatus.java:27-33`
- `infrastructure/entrypoints/rest/constants/ApiConstants.java:29-40`

**Problema:** 7 constantes duplicadas caracter por caracter: `OPERATION_LOAD`, `OPERATION_PROCESS`, `STATUS_LOADING`, `STATUS_PROCESSING`, `STATUS_COMPLETED`, `MSG_LOADING`, `MSG_PROCESSING`.

**Solucion:** `ApiConstants` referencia las de `AsyncOperationStatus` (fuente unica de verdad en el dominio).

**Beneficio:** Sin riesgo de divergencia. Un solo lugar para cambiar.

---

#### P12 — [MEDIA] Eliminar campos no usados en `DocumentRestProperties`

**Archivo:** `infrastructure/entrypoints/rest/config/DocumentRestProperties.java`

- `listPath` (linea 15) — nunca leido
- `getPath` (linea 17) — nunca leido
- `timeoutSeconds` (linea 26) — nunca aplicado al WebClient

**Solucion:** Eliminar los campos no usados. Para `timeoutSeconds`, aplicarlo efectivamente en `ProductRestGatewayAdapter`.

---

#### P13 — [BAJA] Eliminar `MSG_TIMEOUT_TITLE` no usado y Javadoc zombie

**Archivos:**
- `domain/usecase/ProcessingMessages.java:37` — `MSG_TIMEOUT_TITLE` huerfano
- `domain/usecase/DocumentProcessingOrchestrator.java:17-18` — referencia a `AbstractProcessDocumentsUseCase` inexistente

**Solucion:** Eliminar constante. Actualizar Javadoc.

---

#### P14 — [BAJA] Unificar JAXBContext duplicado

**Archivos:**
- `infrastructure/helpers/soap/xml/SoapEnvelopeWrapper.java:31-36`
- `infrastructure/helpers/soap/mapper/SoapMapper.java:29-37`

**Problema:** Dos instancias identicas de `JAXBContext` con las mismas clases. `SoapMapper` ya recibe `SoapEnvelopeWrapper` via inyeccion.

**Solucion:** Exponer `JAXBContext` desde `SoapEnvelopeWrapper` como bean de Spring o compartir via getter.

---

### FASE 3 — Correccion de Naming y Modelo de Dominio

#### P15 — [ALTA] Renombrar `soapCorrelationId` → `correlationId` en dominio y puertos

**Archivos:**
- `domain/entity/ProductDocumentToProcess.java:26` — campo `soapCorrelationId`
- `domain/port/out/ProductDocumentRepository.java:63-64` — parametro `soapCorrelationId`
- `infrastructure/drivenadapters/r2dbc/R2dbcProductDocumentRepository.java:166` — parametro `soapCorrelationId`
- `DatabaseInitializer.java:98` — columna DB `soap_correlation_id`
- `application.yml:23` — `url: jdbc:h2:mem:testdb` (schema DDL)

**Problema:** El dominio usa nombre SOAP-especifico para un concepto generico (correlation ID de servicio externo). S3 usa ETag como correlationId, pero el naming hace parecer que solo SOAP aplica.

**Solucion:** Renombrar `soapCorrelationId` → `correlationId` en todas las capas. La columna DB `soap_correlation_id` requiere migracion.

---

#### P16 — [ALTA] Renombrar bean `soapDocumentSkipHandler` → `documentSkipHandler`

**Archivo:** `application/app-service/config/DomainConfig.java:49, 75, 95`

**Problema:** El bean se llama `soapDocumentSkipHandler` pero se inyecta en **ambos** pipelines (SOAP y S3). La logica de skip no tiene nada de SOAP-especifico.

**Solucion:** Renombrar bean y todas sus referencias.

---

#### P17 — [MEDIA] Renombrar `SoapResponse` → `ExternalServiceResponse` (entidad de dominio)

**Archivo:** `domain/entity/SoapResponse.java`

**Problema:** Clase en `domain.entity` con nombre de protocolo de infraestructura. Sus campos (`status`, `message`, `correlationId`, `traceId`, `processedAt`) son genericos.

**Solucion:** Renombrar a `ExternalServiceResponse` o `GatewayResponse`.

---

#### P18 — [MEDIA] Mover mensajes S3 fuera de `ProcessingMessages` del dominio

**Archivo:** `domain/usecase/ProcessingMessages.java:28-29`

**Problema:** `MSG_UPLOAD_SUCCESS` y `MSG_UPLOAD_FAILURE` son strings especificos de S3 en una clase de constantes del dominio. Solo se usan en `S3GatewayAdapter`.

**Solucion:** Mover como constantes privadas a `S3GatewayAdapter`.

---

#### P19 — [MEDIA] Eliminar constantes `VALUE` redundantes en `ProductStatus` y `DocumentStatus`

**Archivos:**
- `domain/entity/ProductStatus.java:43-49`
- `domain/entity/DocumentStatus.java:15-22`

**Problema:** `PENDING_VALUE = PENDING.name()` — pero `PENDING.name()` ya retorna exactamente lo mismo. Zero valor añadido.

**Solucion:** Eliminar todas las constantes `*_VALUE`. Usar `status.name()` donde sea necesario.

---

#### P20 — [MEDIA] Extraer logica de archivos ZIP de la entidad de dominio `ZipArchive`

**Archivo:** `domain/entity/ZipArchive.java`

**Problema:** Una entidad de dominio contiene `ZipInputStream`, `ByteArrayOutputStream`, buffer 8192. Esto es infraestructura de I/O en el dominio.

**Solucion:** Definir puerto `ArchiveExtractor` en dominio, implementar en infraestructura.

---

### FASE 4 — Observabilidad, Resiliencia y Timeouts

#### P21 — [ALTA] Agregar `%X{traceId}` al patron de log

**Archivo:** `application.yml:38`

**Problema:** El patron actual `"%-5level [%thread] %logger{36} - %msg%n"` no incluye `%X{traceId}`. Un operador en produccion no puede filtrar logs por trace.

**Solucion:**
```yaml
logging:
  pattern:
    console: "%-5level [%thread] [%X{traceId}] %logger{36} - %msg%n"
```

**Beneficio:** Cada linea de log incluye el traceId. `grep trace-abc123 app.log` funciona.

---

#### P22 — [ALTA] Agregar metricas Micrometer en puntos clave

**Archivos nuevos/actualizar:**
- `DocumentProcessingPipeline` — `@Timed("document.processing")`
- `SoapGatewayAdapter` — `Timer` para llamadas SOAP
- `S3GatewayAdapter` — `Timer` para llamadas S3
- `ProductRestGatewayAdapter` — `Timer` para llamadas REST API
- `CircuitBreakerConfiguration` — bindear metricas de CB a Micrometer

**Beneficio:** Visibilidad en Grafana/Prometheus: latencia de gateways, tasa de errores por errorCode, estado del CB.

---

#### P23 — [MEDIA] Agregar `HealthIndicator` para dependencias externas

**Archivo:** Nuevo bean en `application/app-service/config/`

**Problema:** Kubernetes readiness probe muestra UP aunque el servicio SOAP este caido.

**Solucion:** `HealthIndicator` que verifique conectividad basica (ej. TCP connect al endpoint SOAP y REST API product service).

---

#### P24 — [ALTA] Aplicar timeout configurado en `ProductRestGatewayAdapter`

**Archivo:** `infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java:43-51`

**Problema:** `DocumentRestProperties.timeoutSeconds()` existe pero el `WebClient` no tiene timeout. Llamadas al servicio REST pueden colgarse indefinidamente.

**Solucion:**
```java
this.webClient = webClientBuilder
    .baseUrl(properties.endpoint())
    .clientConnector(new ReactorClientHttpConnector(
        HttpClient.create().responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
    ))
    .build();
```

---

#### P25 — [MEDIA] Corregir ubicacion del `.timeout()` en `SoapGatewayAdapter`

**Archivo:** `infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:79`

**Problema:** `.timeout()` esta despues de `.bodyToMono(String.class)`. Solo cubre lectura del body. Conexion TCP y headers HTTP no tienen timeout.

**Solucion:** Configurar `responseTimeout` a nivel `HttpClient`.

---

#### P26 — [ALTA] Agregar timeout al `S3AsyncClient` en `S3GatewayAdapter`

**Archivo:** `infrastructure/drivenadapters/aws/S3GatewayAdapter.java`

**Problema:** `Mono.fromFuture(future)` sin timeout. SDK de AWS puede colgarse. Ademas, el `fromFuture()` no propaga Reactor Context (pierde traceId en MDC).

**Solucion:**
- Agregar `.timeout()` al Mono resultante
- Propagar contexto manualmente o usar `Hooks.enableAutomaticContextPropagation()`

---

### FASE 5 — Tests de Comportamiento y Cobertura Real

#### P27 — [ALTA] Crear tests unitarios para clases nucleo sin cobertura

**Archivos a crear:**
- `DocumentProcessingPipelineTest.java` — probar pipeline con CB mock, verificar stages
- `DocumentProcessingOrchestratorTest.java` — probar flujo pending→process
- `DocumentSenderImplTest.java` — probar envio con ambos resultados (success/failure)
- `DocumentSkipHandlerTest.java` — probar todos los escenarios de skip
- `FileValidatorTest.java` — probar reglas de validacion
- `SoapEnvelopeWrapperTest.java` — probar marshalling/unmarshalling + XML security

**Beneficio:** Cobertura real en el codigo que mas cambia.

---

#### P28 — [ALTA] Crear tests de integracion para repositorios R2DBC

**Archivos a crear:**
- `R2dbcProductDocumentRepositoryTest.java` — probar SQL, Base64 encoding, claim atomico
- `R2dbcProductRepositoryTest.java` — probar queries de productos
- `R2dbcCommunicationLogRepositoryTest.java` — probar inserts/logs

Usar `@DataR2dbcTest` con H2 en modo embedido.

---

#### P29 — [MEDIA] Agregar test de integracion end-to-end

**Archivo nuevo:** `DocumentFlowIntegrationTest.java`

**Problema:** No existe ningun test que pruebe el flujo completo (API → DB → Gateway externo → DB → respuesta). El unico que detectaria errores de wiring en `DomainConfig`.

**Solucion:** `@SpringBootTest` con `WebTestClient` + `MockWebServer` (para SOAP) + `@DataR2dbcTest` (para DB H2):

```java
@Test
void shouldProcessPendingDocumentsAndUpdateStatus() {
    // Given: documentos pendientes insertados en DB H2
    // When: POST /api/products/process?processorType=SOAP&traceId=test-123
    // Then: status COMPLETED, documentos en SUCCESS, communication_log poblado
}
```

---

#### P30 — [MEDIA] Remover `lenient()` de `DocumentValidationRulesTest`

**Archivo:** `domain/usecase/DocumentValidationRulesTest.java`

**Problema:** `lenient()` en todos los stubs (@BeforeEach) significa que Mockito no falla si un stub no se usa. Esto mascara tests que toman ramas inesperadas.

**Solucion:** Remover `lenient()`. Cada test configura solo los stubs que necesita.

---

#### P31 — [MEDIA] Agregar assertions mas fuertes en `LoadProductsUseCaseTest`

**Archivo:** `domain/usecase/LoadProductsUseCaseTest.java:165-208`

**Problema:** El test de expansion ZIP verifica que `saveAll()` fue llamado, pero nunca inspecciona los documentos expandidos (child IDs, parentDocumentId, etc.)

**Solucion:** Capturar `ArgumentCaptor<Flux<ProductDocumentToProcess>>` y verificar contenido de los documentos hijos.

---

#### P32 — [MEDIA] Arreglar `SoapGatewayAdapterTest` para probar caminos alcanzables

**Archivo:** `infrastructure/soap/SoapGatewayAdapterTest.java`

**Problema:** Los tests validan comportamiento (excepciones propagadas) que en produccion es SUPRIMIDO por `onErrorResume`. Despues de P3 (CB fix), los tests deben reflejar el nuevo contrato: fallos de infraestructura → error, fallos de negocio → resultado.

**Tambien:** Agregar test de retry-exhausted.

---

#### P33 — [BAJA] Eliminar tests de properties records sin logica

**Archivos:**
- `DocumentRestPropertiesTest.java`
- `S3PropertiesTest.java`
- `FileUploadPropertiesTest.java`

Si no tienen logica de validacion propia (mas alla de Lombok/records), no justifican test dedicado.

---

## 3. Orden de Implementacion

| Fase | Acciones | Riesgo | Esfuerzo | PR |
|------|----------|--------|----------|-----|
| **Fase 1** | P1–P7 (bugs criticos, CB, subscribe, error codes, race condition) | **Alto** — cambia comportamiento en produccion | 4-6h | PR #1 |
| **Fase 2** | P8–P14 (codigo muerto, constantes, JAXB, tests basura) | **Bajo** — solo eliminacion, sin cambios de comportamiento | 2-3h | PR #2 |
| **Fase 3** | P15–P20 (renombrar soapCorrelationId, SoapResponse, eliminar VALUE constants) | **Medio** — renombres tocan todas las capas, requieren coordinacion DB | 3-4h | PR #3 |
| **Fase 4** | P21–P26 (logging, metricas, timeouts, HealthIndicator) | **Bajo-Medio** — nuevas metricas no rompen funcionalidad, timeouts pueden ser mas agresivos | 3-4h | PR #4 |
| **Fase 5** | P27–P33 (tests de comportamiento, integracion, end-to-end) | **Bajo** — solo agrega tests, sin cambios a produccion | 5-7h | PR #5 |

---

## 4. Riesgos Identificados

| Riesgo | Descripcion | Mitigacion |
|--------|-------------|------------|
| **CB ahora se abre** | Despues de P3, si el servicio externo esta caido, el CB se abrira. Documentos se acumulan como PENDING en vez de marcarse FAILURE inmediatamente. | Validar con negocio que aceptan backlog temporal. El CB se cierra solo tras recovery window. |
| **Renombre DB** | P15 cambia `soap_correlation_id` → `correlation_id` en schema. | Usar tool de migracion (Flyway/Liquibase) o ejecutar en ventana de mantenimiento. |
| **Timeout mas agresivo** | P24 y P25 aplican timeouts explicitos que antes no existian. Llamadas que antes esperaban indefinidamente ahora fallaran. | Revisar valores de timeout en YAML de produccion antes de deploy. |
| **Remover VALUE constants** | P19 elimina constantes como `DocumentStatus.PENDING_VALUE` — codigo externo podria referenciarlas. | Buscar usos con grep en todo el repo antes de eliminar. |
| **Fase 1 en produccion** | Los cambios P1–P7 son estructurales y cambian el flujo de error handling. | Deploy con feature flag o en horario de bajo trafico, monitoreo activo post-deploy. |

---

## 5. Notas de Arquitectura (Decision Records)

### ADR-1: Value Objects `TraceId`/`DocumentId`

**Decision:** Integrar progresivamente, NO eliminar.
**Justificacion:** Ambos revisores reconocieron que el diseño es correcto — el problema es la adopcion incompleta. El costo de integrarlos (cambiar ~20 firmas de metodo) se amortiza con type safety a largo plazo.
**Alternativa rechazada:** Eliminarlos — tira valor real por falta de integracion.

### ADR-2: Reduccion de puertos (5 con 1 sola implementacion)

**Decision:** Diferir para futuro. No es urgente y el riesgo de romper `DomainConfig` es alto.
**Contexto:** 5 de 6 puertos tienen exactamente 1 implementacion (YAGNI). Si el equipo crece y el codigo se estabiliza, consolidar en Fase 6.

### ADR-3: `onErrorResume` en gateways

**Decision:** Mantener `onErrorResume` SOLO para fallos de negocio (HTTP 4xx, SOAP Fault con status FAILURE). Para fallos de infraestructura (timeout, conexion, 5xx tras retry exhaustion), propagar `Mono.error()`.
**Justificacion:** El CB debe reaccionar a fallos de infraestructura. Los fallos de negocio son esperados y no deben abrir el CB.

---

## 6. Pipeline: Estado Actual vs. Estado Objetivo (post-Fase 1–3)

### Actual

```
ProductDocumentToProcess            (13 campos, anemic)
  → [FileValidationRules checks]    (4 imports de ApiConstants en dominio)
  → DocumentSendRequest             (nombre OK)
  → [FileGateway.send()]           
  → SoapGatewayAdapter/S3GatewayAdapter
      → .onErrorResume convierte   TODO error → Mono.just (CB ANULADO)
  → FileUploadResult                (success=true para SKIPPED)
  → [DB status update]             (extraccion fragile por substring)
```

**Problemas:** 4 imports infra→dominio, CB muerto, contadores con race condition, error codes fragiles.

### Objetivo

```
ProductDocumentToProcess            (entidad con comportamiento: claim(), markSucceeded())
  → [validacion directa]            (sin dependencias de infraestructura)
  → DocumentSendRequest             (TraceId/DocumentId value objects)
  → [FileGateway.send()]
  → SoapGatewayAdapter/S3GatewayAdapter
      → infra fall → Mono.error()   (CB FUNCIONAL)
      → negocio fall → Mono.just(FAILURE)
  → FileUploadResult                (success solo para envio real exitoso)
  → [DB status update]              (errorCode tipado desde adapter)
```

**Beneficio:** dominio puro, CB funcional, metricas reales, trazabilidad end-to-end con traceId en logs.

---

## 7. Evaluacion de Mejora Propuesta: Validacion de Archivos por Regex

### Propuesta evaluada

Reemplazar la validacion actual de extensiones de archivo (basada en `Set.contains()` con string comma-separated `allowed-types`) por un enfoque regex:

```java
// PROPUESTA
this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

public boolean isValid(String fileName) {
    if (fileName == null) return false;
    return pattern.matcher(fileName).matches();
}
```

### Veredicto conjunto: RECHAZAR (regex como reemplazo total)

**Ambos revisores coinciden:** NO reemplazar el enfoque actual basado en `Set<String>`. La propuesta introduce mas problemas de los que resuelve.

### Tabla comparativa

| Dimension | Enfoque Actual (`Set.contains`) | Propuesta Regex | Ganador |
|-----------|-------------------------------|-----------------|---------|
| **Rendimiento** | O(1) lookup | O(n) escaneo de string | Actual |
| **Legibilidad** | `allowedTypes.contains(ext)` — inmediato | `pattern.matcher(fileName).matches()` — requiere conocer regex | Actual |
| **Simplicidad de config** | `allowed-types: pdf,txt,csv` en YAML | `regex: ^.*\\.(pdf\|csv)$` — escaping, anclas, alternancia | Actual |
| **Mantenibilidad** | Agregar tipo = append `,docx` | Editar grupo de alternancia | Actual |
| **Manejo de nulos** | `extension()` retorna `""` seguro | `matcher(null)` lanza `NullPointerException` | Actual |
| **Archivos sin extension** | `ext = ""`, no esta en el Set → rechazado | Regex no matchea → rechazado (mismo resultado, pero el actual es explicito) | Empate |
| **Archivos ocultos** (`.gitignore`) | `lastDot > 0` → rechaza correctamente | Riesgo de matchear si no se ancla bien | Actual |
| **Errores de config** | Imposible romper — string literal | `PatternSyntaxException` en runtime si regex es invalido | Actual |

### Middle ground acordado

El **Experto** propuso un camino intermedio aceptado por ambos:

> Si en el futuro se necesita validacion por patron (ej. `"*_draft.pdf"`), agregar un campo **separado** `filenamePatterns` en `FileValidationConfig`. No mezclar validacion de extensiones con validacion de patrones de nombre.

```java
// FileValidationConfig — adicion FUTURA (NO implementar ahora)
Optional<Pattern> filenamePattern(); // ej. "^[a-zA-Z0-9_\\-]+\\.pdf$"
```

**Conclusion:** Mantener el enfoque `Set.contains()` actual. Es mas simple, mas rapido, y no tiene edge cases ocultos.

---

## 8. Oportunidades de Simplificacion Adicionales

Durante la evaluacion de la propuesta regex, ambos revisores identificaron **13 nuevas oportunidades de simplificacion** en el codigo existente.

### Simplificaciones priorizadas (consenso Junior + Experto)

#### S1 — [BAJA] Eliminar `toLowerCase()` redundante en `FileValidator`
**Archivo:** `domain/usecase/FileValidator.java:71`
**Actual:** `if (!allowedTypes.contains(ext.toLowerCase()))` — pero `ext` ya viene lowercased desde `extension()` (linea 53)
**Simplificado:** `if (!allowedTypes.contains(ext))`
**Riesgo:** Baja. Ahorro: 1 llamada innecesaria.

---

#### S2 — [BAJA] Usar `Objects.requireNonNullElse` en lugar de ternarios en `SoapMapper`
**Archivo:** `infrastructure/helpers/soap/mapper/SoapMapper.java:99-102`
**Actual:**
```java
.status(response.getStatus() != null ? response.getStatus() : SoapResponseDefaults.UNKNOWN)
.message(response.getMessage() != null ? response.getMessage() : SoapResponseDefaults.NO_MESSAGE)
```
**Simplificado:**
```java
.status(Objects.requireNonNullElse(response.getStatus(), SoapResponseDefaults.UNKNOWN))
.message(Objects.requireNonNullElse(response.getMessage(), SoapResponseDefaults.NO_MESSAGE))
```
**Riesgo:** Baja. Ahorro: 3 ternarios → 3 llamadas stdlib.

---

#### S3 — [BAJA] Extraer helper `resolveTraceId()` en `ProductHandler`
**Archivo:** `infrastructure/entrypoints/rest/handler/ProductHandler.java:43-46, 71-73`
**Problema:** Logica identica de resolucion traceId duplicada en `loadProducts()` y `processPendingProducts()`.
**Simplificado:**
```java
private static String resolveTraceId(ServerRequest request) {
    String header = request.headers().firstHeader(ApiConstants.HEADER_TRACE_ID);
    return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
}
```
**Riesgo:** Baja. Ahorro: -6 lineas duplicadas, +1 helper de 4 lineas.

---

#### S4 — [BAJA] Unificar encoding/decoding Base64 con `Base64Utils`
**Archivos:**
- `infrastructure/drivenadapters/r2dbc/R2dbcProductDocumentRepository.java:63-75` — metodos privados `decodeContent()`/`encodeContent()` duplican `Base64Utils`
- `infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java:89` — `Base64.getDecoder().decode()` inline
- `infrastructure/helpers/soap/mapper/SoapMapper.java:45` — `Base64.getEncoder().encodeToString()` inline

**Simplificado:** Usar `Base64Utils.decode()` / `Base64Utils.encode()` en los 3 lugares.
**Riesgo:** Baja. `Base64Utils` ya tiene tests.

---

#### S5 — [BAJA] `FileValidationConfig` — agregar `default` methods
**Archivo:** `domain/port/in/FileValidationConfig.java:10-18`
**Problema:** 7 metodos sin defaults — cada implementacion debe declarar los 7, incluso los que no personaliza.
**Simplificado:** Agregar `default` para `foldersToSkip()`, `keywords()`, `originPatternsToSend()`:
```java
default List<String> foldersToSkip() { return List.of(); }
default List<String> keywords() { return List.of(); }
default List<String> originPatternsToSend() { return List.of(); }
```
**Riesgo:** Baja. `ProcessorSettings` elimina 12 lineas de delegacion.

---

#### S6 — [BAJA] `ZipArchive.detectContentType()` — reemplazar if-else con Map
**Archivo:** `domain/entity/ZipArchive.java:75-89`
**Actual:** 6 ramas if-else para mapear extension → MIME type
**Simplificado:** `Map<String, String>` estatico + stream:
```java
private static final Map<String, String> EXT_TO_MIME = Map.of(
    ".pdf", MediaTypeConstants.APPLICATION_PDF,
    ".docx", MediaTypeConstants.APPLICATION_WORD,
    ".txt", MediaTypeConstants.TEXT_PLAIN,
    ".xml", MediaTypeConstants.APPLICATION_XML,
    ".json", MediaTypeConstants.APPLICATION_JSON
);
```
**Riesgo:** Baja.

---

#### S7 — [BAJA] Mover fragmentos de envelope SOAP de `ApiConstants` a `SoapMapper`
**Archivos:**
- `infrastructure/entrypoints/rest/constants/ApiConstants.java:56-59` — 4 constantes de envelope SOAP
- Solo usadas en `SoapMapper.java:62-71`

**Simplificado:** Text block directamente en `SoapMapper.wrapInEnvelope()`:
```java
private String wrapInEnvelope(String soapBody) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <soap:Envelope xmlns:soap="%s" xmlns:file="%s">
          <soap:Header/>
          <soap:Body>
        %s
          </soap:Body>
        </soap:Envelope>
        """.formatted(SoapNamespaces.SOAP_ENVELOPE, SoapNamespaces.FILE_SERVICE, soapBody);
}
```
**Riesgo:** Baja. `ApiConstants` pierde 4 constantes.

---

#### S8 — [MEDIA] Eliminar `SoapResponseDefaults` — 3 constantes en clase dedicada
**Archivo:** `infrastructure/helpers/soap/mapper/SoapResponseDefaults.java` (13 lineas)
**Problema:** 3 strings estaticos en una clase dedicada, usados solo en `SoapMapper.fromSoapXml()`.
**Simplificado:** Mover las constantes a `SoapMapper` como `private static final`.
**Riesgo:** Baja.

---

#### S9 — [MEDIA] `ProductStatusSummary` — reemplazar builder manual con Lombok `@Builder`
**Archivo:** `domain/usecase/ProductStatusSummary.java:20-86`
**Problema:** Builder de 66 lineas escrito a mano. Lombok `@Builder` en record genera exactamente lo mismo.
**Simplificado:** Agregar `@Builder` al record. Eliminar clase interna `ProductStatusSummaryBuilder`.
**Riesgo:** Baja. Call sites no cambian (mismo API `ProductStatusSummary.builder()`).

---

#### S10 — [MEDIA] Unificar extraccion de extension `extension()` duplicada
**Archivos:**
- `FileValidator.java:50-54` — `extension()` con `lastDot > 0` (rechaza leading dots)
- `ProductDocumentInfo.java:16-22` — `extension()` con `lastDot >= 0` (permite leading dots — inconsistente)

**Simplificado:** Extraer a `FileUtils.extension(String filename)` en `domain/util/`. Ambas usan `lastDot > 0`.
**Riesgo:** Media. Cambio de comportamiento en `ProductDocumentInfo.extension()` para archivos como `.gitignore`.

---

#### S11 — [MEDIA] Evaluar convertir `DocumentSendRequest` a record
**Archivo:** `domain/entity/DocumentSendRequest.java`
**Problema:** Clase con `@Getter @Builder` y 8 `private final` campos. Java record nativo daria `equals/hashCode/toString` + accessors.
**Simplificado:**
```java
public record DocumentSendRequest(
    String documentId, byte[] fileContent, String filename,
    String contentType, long fileSize, String traceId,
    String parentFolder, String childFolder
) {
    @Builder public DocumentSendRequest {}
}
```
**Riesgo:** Media. Cambia accessors de `getXxx()` a `xxx()`. Requiere actualizar ~5 call sites.

---

#### S12 — [ALTA] Eliminar `FileUploadProperties` + `app.file.*` — CONFIRMADO por Experto
**Reforzado por el Experto:** `FileUploadProperties` NUNCA es inyectado como bean. Esta registrado en `Application.java:13` y `TestConfig.java:11` pero ningun componente lo consume. `application.yml:40-54` es una copia fantasma de `app.processors.soap.*`.
**Accion:** Eliminar archivo (32 lineas), eliminar seccion YAML (15 lineas), eliminar test (73 lineas), remover de `Application.java` y `TestConfig.java`.
**Riesgo:** Baja.

---

#### S13 — [ALTA] Eliminar value objects `DocumentId`/`TraceId` no usados
**Reforzado por el Experto:** `DocumentId.java` (24 lineas) y `TraceId.java` (37 lineas) NUNCA son referenciados en codigo de produccion. Solo existen en tests.
**Accion:** Eliminar ambos archivos + `DocumentIdTest.java` + `TraceIdTest.java`.
**Riesgo:** Baja.

---

## 9. Auditoria de Integridad de Build y Tests

### Diagnostico del Experto: Build BLOQUEADO por incompatibilidad JDK

El **Experto** identifico la causa raiz por la que `./gradlew build` falla:

**Error:** `java.lang.IllegalArgumentException: 26` en `com.intellij.util.lang.JavaVersion.parse()`
**Causa:** JDK 26 (Temurin-26+35) + Gradle 8.7 con Kotlin 1.9.23. Kotlin no reconoce Java 26 en su mapa de versiones conocido.
**Impacto:** Cero compilacion, cero tests ejecutables desde Gradle.

### Estado real de tests

El **Junior** verifico que el directorio `bin/` contiene 153 `.class` compilados de un build anterior (JDK anterior) y referencias a clases ya eliminadas (`DocumentErrorCodes`, `DocumentResult`, etc.). Estos archivos son **stale artifacts**.

### Correcciones minimas para build limpio (Fase 0 — PRE-REQUISITO)

| Fix | Descripcion | Bloqueante |
|-----|-------------|------------|
| **F0.1** | Configurar Java toolchain 21 en `build.gradle.kts` o usar JDK 21 | **SI** — sin esto Gradle no arranca |
| **F0.2** | Eliminar directorio `bin/` con artifacts stale | **SI** — confusion garantizada |
| **F0.3** | Corregir package de `SoapGatewayAdapterTest` (dice `infrastructure.soap`, deberia ser `infrastructure.drivenadapters.soap`) | No |
| **F0.4** | Eliminar `compile_errors.log`, `final_compile.log` y otros logs de build stale en raiz del proyecto | No |

---

## 10. Fases Actualizadas del Plan (incluye Fase 0 y Fase 6)

| Fase | Contenido | Riesgo | PR |
|------|-----------|--------|-----|
| **Fase 0** | F0.1–F0.4: Pre-requisitos de build — JDK 21 toolchain, eliminar bin/ y logs stale | **Critico** — sin esto nada compila | PR #0 |
| **Fase 1** | P1–P7: Bugs criticos — purificar dominio, arreglar CB, eliminar subscribes, race condition | **Alto** | PR #1 |
| **Fase 2** | P8–P14 + S12 + S13: Codigo muerto — `FileUploadProperties`, value objects, tests basura, JAXB duplicado | **Bajo** | PR #2 |
| **Fase 3** | P15–P20: Naming — `soapCorrelationId`→`correlationId`, `SoapResponse`→`ExternalServiceResponse`, bean naming | **Medio** | PR #3 |
| **Fase 4** | P21–P26: Observabilidad — MDC traceId, metricas Micrometer, HealthIndicators, timeouts | **Bajo-Medio** | PR #4 |
| **Fase 5** | P27–P31: Tests de comportamiento reales | **Bajo** | PR #5 |
| **Fase 6** | S1–S11: Simplificaciones (stdlib, text blocks, DRY, records) | **Bajo** | PR #6 |

---

## 11. Criterios de Finalizacion del Plan (OBLIGATORIO)

> **EL PLAN NO SE CONSIDERA COMPLETADO HASTA QUE SE CUMPLAN TODOS LOS SIGUIENTES CRITERIOS.**

### C1 — Compilacion limpia
- [ ] `./gradlew build` ejecuta sin errores
- [ ] `./gradlew compileJava` produce clases actualizadas en `build/`
- [ ] Cero errores de compilacion en todas las clases `src/main/`

### C2 — Tests unitarios pasando
- [ ] `./gradlew test` ejecuta todos los tests sin failures
- [ ] Cero tests `@Disabled` sin justificacion documentada
- [ ] Tests de reflexion (`Class.forName`) eliminados o reemplazados
- [ ] Cobertura de tests > 0% en todas las clases nucleo: `DocumentProcessingPipeline`, `DocumentProcessingOrchestrator`, `FileValidator`, `DocumentSenderImpl`, `DocumentSkipHandler`

### C3 — Microservicio ejecutando
- [ ] `./gradlew bootRun` levanta la aplicacion sin errores
- [ ] Health endpoint `/actuator/health` responde `UP`
- [ ] Endpoints REST (`/api/products/load`, `/api/products/process`) aceptan requests y retornan respuestas validas
- [ ] No hay `ErrorCallbackNotImplemented` ni errores de contexto en logs de arranque

### C4 — Verificacion de logs
- [ ] Cada linea de log incluye `[traceId]` en el patron
- [ ] TraceId consistente a traves de todas las capas (no se regenera en cada paso)

### C5 — Plan actualizado
- [ ] `Plan_refac.md` refleja el estado real: items completados tachados, items pendientes claros
- [ ] Issues encontrados durante ejecucion/pruebas documentados como nuevas acciones

---

## 12. Nota Final sobre Regex

> *"Set.contains es O(1). Pattern.matcher es O(n). Para validar extensiones de archivo, Set gana en velocidad, claridad, y seguridad. Regex para esto es over-engineering."* — Junior Backend Engineer
>
> *"No reemplacen simplicidad con flexibilidad que nadie pidio. Si algun dia necesitan regex para patrones de nombre, agreguen un campo separado. No mezclen concerns."* — Expert Backend Engineer

**La propuesta regex queda RECHAZADA como reemplazo. El middle ground (campo `filenamePattern` opcional futuro) queda documentado para referencia.**
