# Plan de Remediacion: Auditoria de Trazabilidad

**Fuente**: Auditoria combinada de dos agentes senior (Backend + QA, 20 anos exp cada uno)
**Fecha**: 2026-05-07
**Objetivo**: Corregir bugs criticos, cerrar gaps de cobertura de tests, y preparar el feature de trazabilidad para produccion.

---

## I. Clasificacion de Severidad

| Nivel | Criterio |
|---|---|
| **CRITICO** | Rompe produccion, causa perdida de datos, o invalida el proposito del feature |
| **IMPORTANTE** | Degrada funcionalidad, causa loops infinitos, o deja documentos stuck |
| **RECOMENDADO** | Mejora diseno, performance, mantenibilidad, o cobertura de tests |

---

## II. Hallazgos y Plan de Correccion

### CRITICO-1: Retries de S3 rotos — `Mono.fromFuture` reutiliza el mismo `CompletableFuture`

**Archivo**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/aws/S3GatewayAdapter.java`
**Lineas**: 80-105
**Raiz**: `CompletableFuture<PutObjectResponse> future = s3Client.putObject(...)` se ejecuta UNA vez dentro del `Mono.deferContextual()`. El `Mono.fromFuture(future)` empaqueta ese mismo future. Cuando `retryWhen` reintenta, se re-suscribe al MISMO future ya completado. No se crea jamas una nueva llamada a S3.

**Fix**: Envolver la creacion del future en `Mono.defer()` dentro del pipeline retryeable, para que cada reintento ejecute una nueva llamada `s3Client.putObject()`.

```java
// ANTES (lines 80-105):
CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));

return Mono.fromFuture(future)
    .timeout(Duration.ofSeconds(s3Properties.timeoutSeconds()))
    .retryWhen(...)
    .map(completed -> { ... })
    .onErrorResume(error -> handleS3Error(error, request.getDocumentId(), traceId));

// DESPUES:
return Mono.defer(() -> {
        CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));
        return Mono.fromFuture(future);
    })
    .timeout(Duration.ofSeconds(s3Properties.timeoutSeconds()))
    .retryWhen(Retry.backoff(s3Properties.retryAttempts(), Duration.ofMillis(s3Properties.retryBackoffMillis()))
        .filter(this::isRetryableException)
        .doBeforeRetry(retrySignal -> {
            traceRetry(request, retrySignal);
            long attempt = retrySignal.totalRetries() + 1;
            log.log(Level.WARNING, "Retrying S3 upload for documentId={0}, attempt {1}/{2} (backoff={3}ms)",
                new Object[]{request.getDocumentId(), attempt, s3Properties.retryAttempts(),
                s3Properties.retryBackoffMillis() * attempt});
        }))
    .map(completed -> { ... })
    .onErrorResume(error -> handleS3Error(error, request.getDocumentId(), traceId));
```

**Tests requeridos**: Ver test S3GatewayAdapterTest `#T1` en seccion IV.

---

### CRITICO-2: `handleSuccess` no verifica `result.isSuccess()` — fallos se tracean como EXITO

**Archivo**: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`
**Lineas**: 98-102
**Raiz**: `handleSuccess` llama incondicionalmente a `traceSuccess` y `updateStateById(PROCESSED)` sin verificar si `result.isSuccess()` es `true`. Ambos adaptadores (S3 y SOAP) convierten errores en `Mono.just(FileUploadResult)` con `success=false`. Un fallo total de upload llega a `handleSuccess` como si fuera exito.

**Fix**: Agregar guard al inicio de `handleSuccess`:

```java
// ANTES (lines 98-102):
private Mono<FileUploadResult> handleSuccess(Document doc, FileUploadResult result, String filename) {
    traceSuccess(doc.id(), filename, implementationName());
    documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
    return Mono.just(result);
}

// DESPUES:
private Mono<FileUploadResult> handleSuccess(Document doc, FileUploadResult result, String filename) {
    if (!result.isSuccess()) {
        String errorCode = result.getErrorCode() != null ? result.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        return handleError(doc, new ProcessingException(errorCode,
            result.getMessage() != null ? result.getMessage() : "Upload returned failure status"), filename);
    }
    traceSuccess(doc.id(), filename, implementationName());
    documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
    return Mono.just(result);
}
```

**Tests requeridos**: Ver test S3DocumentProcessingUseCaseTest `#T5`, SoapDocumentProcessingUseCaseTest `#T6` en seccion IV.

---

### CRITICO-3: NPE en validacion de patron — `doc.name()` es siempre null, debe ser `doc.filename()`

**Archivo**: `src/main/java/com/example/fileprocessor/domain/service/RulesBussinesService.java`
**Lineas**: 46-51
**Raiz**: `filenamePattern.matcher(doc.name())` — `ProductDocumentHistory.name()` nunca se setea en `toProductDocument()` ni en `ZipDecompressor.buildProductDocument()`. `Pattern.matcher(null)` no hace null-check y lanza NPE. Todo documento que pase por validacion con `filenamePattern` configurado entra en loop infinito: NPE -> `UNKNOWN_ERROR` -> re-queue como PENDING.

**Fix**: Cambiar `doc.name()` por `doc.filename()` en ambas ocurrencias:

```java
// ANTES (lines 46-51):
if (filenamePattern != null && !filenamePattern.matcher(doc.name()).matches()) {
    return Mono.error(new ProcessingException(
        ProcessingResultCodes.PATTERN_MISMATCH,
        String.format("Filename '%s' does not match pattern '%s'",
            doc.name(), filenamePattern.pattern())));
}

// DESPUES:
if (filenamePattern != null && !filenamePattern.matcher(doc.filename()).matches()) {
    return Mono.error(new ProcessingException(
        ProcessingResultCodes.PATTERN_MISMATCH,
        String.format("Filename '%s' does not match pattern '%s'",
            doc.filename(), filenamePattern.pattern())));
}
```

**Tests requeridos**: Ver test DocumentValidatorTest `#T4` en seccion IV.

---

### IMPORTANTE-4: `updateStateById` sin `.doOnError()` — documentos pueden quedar stuck en IN_PROGRESS

**Archivo**: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`
**Lineas**: 54, 58, 94, 100, 108
**Raiz**: Cinco llamadas a `updateStateById(...).subscribe()` sin `.doOnError()`. Si la BD falla, la excepcion va a `System.err` via el default Reactor hooks subscriber. El documento queda en estado incorrecto.

**Fix**: Agregar `.doOnError()` en los 5 sitios:

```java
// Linea 54: PENDING -> IN_PROGRESS
documentRepository.updateStateById(doc.id(), ProductState.IN_PROGRESS, LocalDateTime.now())
    .doOnError(e -> log.log(Level.SEVERE, "Failed to set IN_PROGRESS for doc {0}: {1}",
        new Object[]{doc.id(), e.getMessage()}))
    .subscribe();

// Linea 58: reset a PENDING
documentRepository.updateStateById(doc.id(), ProductState.PENDING, LocalDateTime.now())
    .doOnError(e -> log.log(Level.SEVERE, "Failed to reset PENDING for doc {0}: {1}",
        new Object[]{doc.id(), e.getMessage()}))
    .subscribe();

// Linea 94: skipDocByBussines -> PROCESSED
documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now())
    .doOnError(e -> log.log(Level.SEVERE, "Failed to set PROCESSED (skip) for doc {0}: {1}",
        new Object[]{doc.id(), e.getMessage()}))
    .subscribe();

// Linea 100: handleSuccess -> PROCESSED
documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now())
    .doOnError(e -> log.log(Level.SEVERE, "Failed to set PROCESSED for doc {0}: {1}",
        new Object[]{doc.id(), e.getMessage()}))
    .subscribe();

// Linea 108: handleError -> FAILED
documentRepository.updateStateById(doc.id(), ProductState.FAILED, LocalDateTime.now())
    .doOnError(e -> log.log(Level.SEVERE, "Failed to set FAILED for doc {0}: {1}",
        new Object[]{doc.id(), e.getMessage()}))
    .subscribe();
```

---

### IMPORTANTE-5: Race condition multi-instancia — `PENDING -> IN_PROGRESS` no es atomico

**Archivo**: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`
**Lineas**: 46-47 (findByStateAndUseCase) + 54 (updateStateById)
**Raiz**: `findByStateAndUseCase(PENDING)` retorna documentos, luego `concatMap` llama a `startProcessing` que hace `updateStateById(IN_PROGRESS).subscribe()`. Entre el SELECT y el UPDATE, otra instancia puede tomar el mismo documento. En Kubernetes con 3 replicas, un mismo documento puede ser procesado simultaneamente por 2 pods.

**Fix**: Modificar `DocumentRepository.updateStateById` para que sea atomico con condicion `WHERE estado = 'PENDING'`, y retornar las filas afectadas. Si retorna 0, el documento ya fue tomado por otra instancia y se debe skipear.

Paso 1 — Cambiar `DocumentRepository.updateStateById` para que retorne `Mono<Long>` (filas afectadas):

```java
// DocumentRepository.java — cambiar firma de:
Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt);
// a:
Mono<Long> updateStateById(Long id, String expectedState, String newState, LocalDateTime updatedAt);
```

Paso 2 — En `startProcessing`, verificar el resultado:

```java
private Flux<FileUploadResult> startProcessing(Document doc) {
    return documentRepository.updateStateById(doc.id(), ProductState.PENDING, ProductState.IN_PROGRESS, LocalDateTime.now())
        .flatMapMany(rowsAffected -> {
            if (rowsAffected == 0) {
                log.log(Level.INFO, "Document {0} already claimed by another instance, skipping", doc.id());
                return Flux.empty();
            }
            return processDocument(doc)
                .onErrorResume(error -> {
                    traceFailure(doc.id(), doc.name(), implementationName(), error);
                    documentRepository.updateStateById(doc.id(), ProductState.IN_PROGRESS, ProductState.PENDING, LocalDateTime.now())
                        .doOnError(e -> log.log(Level.SEVERE, "Failed to reset PENDING for doc {0}", doc.id()))
                        .subscribe();
                    log.log(Level.SEVERE, "Document {0} failed unexpectedly, re-queued: {1}",
                        new Object[]{doc.documentId(), error.getMessage()});
                    return Flux.empty();
                });
        });
}
```

**Impacto en tests**: Todos los stubs de `updateStateById` deben cambiar a retornar `Mono.just(1L)` en vez de `Mono.empty()`.

---

### IMPORTANTE-6: SOAP V2 (`transmitirDocumento`) no esta cableado al pipeline

**Archivo**: `src/main/java/com/example/fileprocessor/domain/usecase/SoapDocumentProcessingUseCase.java`
**Lineas**: 31-38
**Raiz**: `uploadDocument` solo llama a `soapGateway.send()` (V1). El metodo V2 (`transmitirDocumento`) existe en `SoapGatewayAdapter` implementando `SoapGatewayV2`, pero ningun UseCase lo invoca. El adapter tiene ambas interfaces pero el pipeline solo usa V1.

**Fix**: Modificar `SoapDocumentProcessingUseCase` para decidir entre V1 y V2 segun resultado de homologacion o configuracion:

```java
@Override
protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
    return homologationRepository.resolve(doc.origin(), doc.pais())
        .flatMap(result -> {
            FileUploadRequest request = buildFileUploadRequest(doc, result.origin(), result.paisHomologado(), docId);
            if (result.useV2()) {
                SoapGatewayV2 v2Gateway = (SoapGatewayV2) soapGateway;
                return v2Gateway.transmitirDocumento(request);
            }
            return soapGateway.send(request);
        })
        .onErrorResume(this::handleUploadError);
}
```

**Pre-requisito**: `HomologationResult` debe exponer un flag `useV2()` (si no existe, agregarlo; si la homologacion no determina version, usar configuracion `ProcessorsProperties`).

---

### IMPORTANTE-7: `ZipDecompressor` usa `documentId` como `traceId` semanticamente incorrecto

**Archivo**: `src/main/java/com/example/fileprocessor/domain/util/ZipDecompressor.java`
**Lineas**: 47-49
**Raiz**: `ProcessingException.withTraceId("Failed to decompress ZIP: " + zipDoc.documentId(), ProcessingResultCodes.INVALID_ZIP, zipDoc.documentId())` — el tercer argumento es semanticamente `traceId`, pero se le pasa `zipDoc.documentId()`. Aunque funcionalmente no rompe nada, el mensaje de error incluye el documentId en lugar del traceId real.

**Fix**: Cambiar a usar el nuevo constructor sin traceId:

```java
// ANTES:
throw ProcessingException.withTraceId(
    "Failed to decompress ZIP: " + zipDoc.documentId(),
    ProcessingResultCodes.INVALID_ZIP, zipDoc.documentId());

// DESPUES (usando constructor (errorCode, message, cause)):
throw new ProcessingException(
    ProcessingResultCodes.INVALID_ZIP,
    "Failed to decompress ZIP '" + zipDoc.filename() + "': " + e.getMessage(),
    e);
```

Nota: Esto cambia `INVALID_ZIP` como codigo de error. Evaluar si debe ser `DECOMPRESSION_ERROR` para consistencia con el `onErrorMap` de `decompressIfNeeded` (AbstractDocumentProcessingUseCase:85-88). Los codigos quedarian duplicados: `INVALID_ZIP` y `DECOMPRESSION_ERROR`. **Recomendacion**: unificar en `DECOMPRESSION_ERROR` y eliminar `INVALID_ZIP`.

---


## III. Secuencia de Implementacion

| Fase | Items | Prioridad | Estimacion |
|---|---|---|---|
| **Fase 1: Correccion de bugs criticos** | CRITICO-1, CRITICO-2, CRITICO-3 | CRITICO | 2h |
| **Fase 2: Resiliencia de estado** | IMPORTANTE-4, IMPORTANTE-5 | IMPORTANTE | 3h |
| **Fase 3: Cableado SOAP V2** | IMPORTANTE-6, IMPORTANTE-7 | IMPORTANTE | 2h |
| **Fase 4: Tests unitarios** | T1 a T18 (ver seccion IV) | IMPORTANTE | 5h |
| **Fase 5: Tests de integracion** | T15 a T16 (ver seccion IV) | IMPORTANTE | 3h |

**Total estimado**: ~15h

### Orden de ejecucion obligatorio:

```
Fase 1 (bugs criticos)
  └──> Fase 2 (resiliencia)
         └──> Fase 3 (V2 cableado)
                └──> Fase 4 (tests unitarios)
                       └──> Fase 5 (tests integracion)
```

### Verificacion por fase:
- Fase 1: `./gradlew test` debe pasar los tests existentes + no deben existir los bugs documentados
- Fase 2: Test integracion multi-instancia (dos hilos concurrentes tomando documentos PENDING)
- Fase 3: WireMock con V1 y V2 muestra trazas separadas
- Fase 4: Cobertura > 80% de los 27 escenarios (actual: 30%)
- Fase 5: E2E funcional con Testcontainers

---

## IV. Plan de Nuevos Tests

### Tests de adapter S3 (`S3GatewayAdapterTest.java`)

#### #T1: `send_withRetries_eventualSuccess_createsRetryTraces`
**Cubre escenarios**: E6 (S3 retry 1,2 fallan, 3 exito)
**Setup de mock**:
- Intento 1: `s3Client.putObject` retorna `CompletableFuture` que falla con `SdkException` (ServiceException)
- Intento 2: mismo future falla con `TimeoutException`
- Intento 3: retorna `CompletableFuture.completedFuture(PutObjectResponse)`
- Stub `historyRepository.save()` retorna `Mono.empty()`
- `s3Properties.retryAttempts = 3`, `s3Properties.retryBackoffMillis = 10` (minimizar tiempo)
**Verificar**:
- `historyRepository.save` se llamo **exactamente 2 veces** (traceRetry para intentos 1 y 2)
- Argumento 1er save: `retry = 1`, `result = FAILURE`
- Argumento 2do save: `retry = 2`, `result = FAILURE`
- Resultado final: `success = true`, `correlationId` presente
- NO se llamo `historyRepository.save` para exito (eso lo hace el use case)

#### #T2: `send_withRetries_allExhausted_returnsErrorResult`
**Cubre escenarios**: E7 (S3 todos los retries agotados)
**Setup de mock**:
- Todos los intentos fallan con `S3Exception(503)` retryable
- `retryAttempts = 3`
**Verificar**:
- `historyRepository.save` se llamo **3 veces** (una por cada intento)
- `retry = 1, 2, 3` respectivamente
- Resultado final: `success = false`, `errorCode = SERVICE_UNAVAILABLE`

#### #T3: `send_dbErrorDuringRetryTrace_pipelineContinues`
**Cubre escenarios**: E10 (fallo BD al guardar traza)
**Setup de mock**:
- Intento 1 falla con error retryable
- `historyRepository.save()` retorna `Mono.error(new RuntimeException("DB down"))`
- Intento 2 exito
**Verificar**:
- El pipeline NO se interrumpe (resultado final success)
- Se llamo `historyRepository.save` (aunque fallo, se intento)

### Tests de adapter SOAP (`SoapGatewayAdapterTest.java`)

#### #T4: `send_withRetries_eventualSuccess_createsRetryTraces`
**Cubre escenarios**: E19 (timeout con reintentos)
**Setup de mock**:
- Intento 1: `bodyToMono` retorna `Mono.error(new TimeoutException())`
- Intento 2: `bodyToMono` retorna `Mono.error(new WebClientResponseException(503))`
- Intento 3: `bodyToMono` retorna `Mono.just("<response>ok</response>")`
- `soapMapper.fromSoapXml` retorna `successResponse()`
**Verificar**:
- `historyRepository.save` se llamo **2 veces** (traceRetry intentos 1 y 2)
- Argumentos: `retry = 1` con `errorCode = GATEWAY_TIMEOUT`, `retry = 2` con `errorCode = BAD_GATEWAY`
- Resultado final success

#### #T5: `send_withRetries_allExhausted_returnsErrorWithTraces`
**Cubre escenarios**: E8 (SOAP todos los retries agotados)
**Setup de mock**: Todos los intentos fallan con `WebClientResponseException(503)`, `maxRetries = 3`
**Verificar**:
- `historyRepository.save` se llamo **3 veces**
- Resultado final: `success = false`, `errorCode = BAD_GATEWAY`

#### #T6: `send_http500_noRetryAttempted_noTraceCreated`
**Cubre escenarios**: E18 (HTTP 500 no retryable)
**Setup de mock**: `WebClientResponseException(500)`, `retryAttempts = 3`
**Verificar**:
- `historyRepository.save` **NUNCA** se llamo (verify(historyRepository, never()).save(any()))
- Resultado final: `errorCode = BAD_GATEWAY`

#### #T7: `send_ioException_noRetryAttempted_noTraceCreated`
**Cubre escenarios**: E20 (IOException no retryable)
**Setup de mock**: `IOException` como error en `bodyToMono`
**Verificar**:
- `historyRepository.save` **NUNCA** se llamo
- Resultado final: `errorCode = UNKNOWN_ERROR`

#### #T8: `send_mapperThrowsException_handledGracefully`
**Cubre escenarios**: E25 (error en soapMapper.toFullSoapMessage)
**Setup de mock**: `soapMapper.toFullSoapMessage()` lanza `RuntimeException`
**Verificar**:
- Resultado final: `errorCode = UNKNOWN_ERROR`
- No hay interaccion con `historyRepository`

### Tests de validacion (`DocumentValidatorTest.java`)

#### #T9: `validate_sizeExceeded_throwsProcessingException`
**Cubre escenarios**: E3 (SIZE_EXCEEDED)
**Setup**: `maxFileSizeBytes = 100`, documento con `size = 500`
**Verificar**: `StepVerifier` verifica `ProcessingException` con mensaje que contiene "exceeds max" y `errorCode = SIZE_EXCEEDED`

#### #T10: `validate_patternMismatch_throwsProcessingException` (existente, renombrar)
**Cubre escenarios**: E4 (PATTERN_MISMATCH)
**Existente**: `validate_patternFails` — verificar que usa `doc.filename()` (no `doc.name()`)

### Tests de caso de uso S3 (`S3DocumentProcessingUseCaseTest.java`)

#### #T11: `uploadDocument_whenGatewayReturnsFailureStatus_routesToError`
**Cubre escenarios**: Verifica que CRITICO-2 esta corregido
**Setup de mock**:
- `s3Gateway.send()` retorna `Mono.just(FileUploadResult(Failure, SERVICE_UNAVAILABLE))`
- Stubs para `historyRepository.findLastAudit`, `historyRepository.save`, `documentRepository.updateStateById`
**Verificar**:
- Resultado: `success = false`, `errorCode = SERVICE_UNAVAILABLE` o `UNKNOWN_ERROR`
- `historyRepository.save` fue llamado con `result = FAILURE` (NO success)
- `documentRepository.updateStateById` fue llamado con `FAILED` (NO PROCESSED)

#### #T12: `executePendingDocuments_singleDocument_fullPipelineSuccess`
**Cubre escenarios**: E1 (golden path)
**Setup de mock**:
- `documentRepository.findByStateAndUseCase` retorna `Flux.just(documento PENDING)`
- `productRestGateway.getDocument` retorna `ProductDocumentFile` valido (no ZIP)
- `documentValidator.validate` retorna el mismo documento
- `s3Gateway.send` retorna `FileUploadResult(SUCCESS)`
**Verificar**: `StepVerifier` emite 1 resultado success. Verificar `historyRepository.save` llamado con `SUCCESS`.

### Tests de caso de uso SOAP (`SoapDocumentProcessingUseCaseTest.java`)

#### #T13: `uploadDocument_homologationFails_propagatesError`
**Cubre escenarios**: E17 (fallo homologacion)
**Setup de mock**: `homologationRepository.resolve` retorna `Mono.error(RuntimeException)`
**Verificar**: `StepVerifier` emite failure. `soapGateway.send` NUNCA se llama.

#### #T14: `uploadDocument_whenGatewayReturnsFailureStatus_routesToError`
**Cubre escenarios**: Analogo a #T11 pero para SOAP
**Verificar**: `historyRepository.save` llamado con `FAILURE`, no `SUCCESS`.

### Tests de integracion E2E (`SoapDocumentProcessingE2ETest.java` — nuevo archivo)

#### #T15: `e2e_processPendingDocument_successTraceInDatabase`
**Cubre escenarios**: E26 (E2E con Postgres + WireMock)
**Infraestructura**: Testcontainers Postgres + WireMock
**Setup**:
- Insertar documento PENDING en tabla `documentos` via repositorio R2DBC real
- WireMock stub: `POST /soap` responde 200 con SOAP response valido
- Disparar `executePendingDocuments()`
**Verificar**:
- Respuesta final NDJSON contiene 1 resultado SUCCESS
- `SELECT * FROM historico_documentos_new WHERE documento_id = ?` retorna 1 fila con `resultado = SUCCESS`
- `SELECT estado FROM documentos WHERE id = ?` retorna `PROCESSED`

#### #T16: `e2e_processPendingDocument_retriesWithTraceInDatabase`
**Cubre escenarios**: E6/E8
**WireMock escenario**:
- Stub mapping 1: responde 503 (retryable)
- Stub mapping 2: responde 503
- Stub mapping 3: responde 200
**Verificar**: 2 trazas FAILURE con retry=1,2 + 1 traza SUCCESS en BD.

### Tests de proteccion (`TraceFailureTest.java` o en `AbstractDocumentProcessingUseCaseTest.java` — nuevo)

#### #T17: `traceFailure_nullRetryInLastAudit_defaultsTo1`
**Cubre escenarios**: E12
**Mock**: `findLastAudit` retorna `DocumentHistory` con `retry = null`
**Verificar**: `save` es llamado con `retry = 1`

#### #T18: `traceFailure_emptyFindLastAudit_defaultsToRetry1`
**Cubre escenarios**: E11
**Mock**: `findLastAudit` retorna `Mono.empty()`
**Verificar**: `save` es llamado con `retry = 1` (via `defaultIfEmpty`)

---

## V. Matriz de Cobertura Post-Remediacion

| Escenario | Test actual | Test nuevo | Cobertura post |
|---|---|---|---|
| E1 (golden path) | parcial (uploadDocument solo) | T12 (pipeline completo) | COMPLETO |
| E2 (ZIP expandido) | no | T12 extendido con ZIP mock | COMPLETO |
| E3 (SIZE_EXCEEDED) | no | T9 | COMPLETO |
| E4 (PATTERN_MISMATCH) | si (corregido NPE) | T10 | COMPLETO |
| E5 (DECOMPRESSION_ERROR) | no | via pipeline test + mock | COMPLETO |
| E6 (S3 retry 1,2 fallan, 3 exito) | no | T1 | COMPLETO |
| E7 (S3 retries agotados) | no | T2 | COMPLETO |
| E8 (SOAP retries agotados) | no | T5 | COMPLETO |
| E9 (error inesperado) | parcial | verificado indirectamente | PARCIAL |
| E10 (fallo BD traza) | no | T3 | COMPLETO |
| E11 (findLastAudit empty) | no | T18 | COMPLETO |
| E12 (findLastAudit retry null) | no | T17 | COMPLETO |
| E13 (concurrencia 3 docs) | no | test concurrencia (opcional) | PARCIAL |
| E14 (content vacio) | si | existente OK | COMPLETO |
| E15-E16 (SOAP V1/V2 golden) | parcial | T12 SOAP version | COMPLETO |
| E17 (fallo homologacion) | no | T13 | COMPLETO |
| E18 (HTTP 500 no retryable) | parcial | T6 | COMPLETO |
| E19 (timeout con retries) | no | T4 | COMPLETO |
| E20 (IOException no retryable) | parcial | T7 | COMPLETO |
| E21 (sin traceId) | si | existente OK | COMPLETO |
| E22 (V1+V2 paralelo) | no | T15/T16 (E2E) | PARCIAL |
| E23 (request enriquecido) | parcial | via T12 con assertion | COMPLETO |
| E24 (maxRetries=0) | si | existente OK (se usa config) | COMPLETO |
| E25 (error soapMapper) | no | T8 | COMPLETO |
| E26 (E2E SOAP) | no | T15 | COMPLETO |
| E27 (E2E 400 Bad Request) | no | variante de T15 con WireMock 400 | COMPLETO |

**Cobertura estimada post-remediacion**: 24/27 completo, 3/27 parcial → **~90%**

---

## VI. Archivos Afectados (Resumen)

### Archivos a modificar:

| # | Archivo | Fase | Cambio |
|---|---|---|---|
| 1 | `S3GatewayAdapter.java` | Fase 1 | `Mono.defer()` wrapper para retries (CRITICO-1) |
| 2 | `AbstractDocumentProcessingUseCase.java` | Fase 1 | Guard `isSuccess()` en handleSuccess (CRITICO-2) |
| 3 | `RulesBussinesService.java` | Fase 1 | `doc.name()` → `doc.filename()` (CRITICO-3) |
| 4 | `AbstractDocumentProcessingUseCase.java` | Fase 2 | `.doOnError()` en 5 suscripciones (IMPORTANTE-4) |
| 5 | `DocumentRepository.java` | Fase 2 | `updateStateById` atomico con WHERE estado= (IMPORTANTE-5) |
| 6 | `AbstractDocumentProcessingUseCase.java` | Fase 2 | `startProcessing` con verificacion rowsAffected (IMPORTANTE-5) |
| 7 | `SoapDocumentProcessingUseCase.java` | Fase 3 | Cableado V1 vs V2 (IMPORTANTE-6) |
| 8 | `ZipDecompressor.java` | Fase 3 | Constructor ProcessingException correcto (IMPORTANTE-7) |
| 9 | `S3GatewayAdapterTest.java` | Fase 4 | T1, T2, T3 |
| 10 | `SoapGatewayAdapterTest.java` | Fase 4 | T4, T5, T6, T7, T8 |
| 11 | `DocumentValidatorTest.java` | Fase 4 | T9, correccion T10 |
| 12 | `S3DocumentProcessingUseCaseTest.java` | Fase 4 | T11, T12 |
| 13 | `SoapDocumentProcessingUseCaseTest.java` | Fase 4 | T13, T14 |
| 14 | `ProcessingResultCodes.java` | Fase 3 | Unificar `INVALID_ZIP` → `DECOMPRESSION_ERROR` |

### Archivos nuevos:

| # | Archivo | Fase |
|---|---|---|
| 15 | `SoapDocumentProcessingE2ETest.java` | Fase 5 |
| 16 | `AbstractDocumentProcessingUseCaseTest.java` | Fase 4 — tests T17, T18 |

---

## VII. Veredicto Final

El feature de trazabilidad tiene una base solida en diseno (patron fire-and-forget, separacion de responsabilidades entre use case y adapters, 27 escenarios documentados). Sin embargo, **no esta listo para produccion** debido a:

1. **Tres bugs criticos** que causan: (a) retries de S3 que no funcionan, (b) fallos traçados como exito, (c) NPE que causa loops infinitos.
2. **Falta de tests**: Solo el 30% de los escenarios tienen cobertura. Los tests existentes no ejercitan el pipeline completo.
3. **Fragilidad operacional**: Sin recuperacion de documentos stuck, sin transiciones atomicas multi-instancia.

**Despues de ejecutar este plan (Fases 1-5)**, el feature seria production-ready con ~90% de cobertura de escenarios y manejo de errores robusto.
