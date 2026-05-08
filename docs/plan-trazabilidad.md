# Plan: Trazabilidad de Tratamiento y Envio de Documentos

## Contexto

Actualmente `AbstractDocumentProcessingUseCase` procesa documentos (obtener -> descomprimir -> validar -> subir) pero **no guarda trazabilidad** en `historico_documentos`. La tabla ya existe con su entidad (`DocumentHistory`), puerto (`DocumentHistoryRepository`) y adapter (`DocumentHistoryR2dbcAdapter`) funcionando.

**Objetivo**: Registrar cada evento del pipeline en `historico_documentos` con **trazabilidad especifica** que indique exactamente que regla de negocio aplico, que fallo ocurrio, o si fue exitoso. La trazabilidad debe ser **fire-and-forget**: si falla, solo se loguea, nunca interrumpe el pipeline principal.

**Regla de dominio**: Solo se modifica `estado` en la tabla `documentos`.

---

## Decision de diseno

Se inyecta directamente `DocumentHistoryRepository` (puerto de dominio ya existente) en `AbstractDocumentProcessingUseCase` como 4ta dependencia. No se crean clases nuevas.

Para saber **exactamente que regla** rechazo un documento, se modifica `RulesBussinesService.validate()` para que en vez de retornar `Mono.empty()` silenciosamente, **lance `ProcessingException`** con codigos de error especificos (`SIZE_EXCEEDED`, `PATTERN_MISMATCH`). En el pipeline, `.switchIfEmpty()` se reemplaza por `.onErrorResume(ProcessingException.class, ...)` que captura la excepcion y llama a `skipDocByBussines` con los detalles del rechazo.

**Ventaja**: la traza registra exactamente que regla se aplico y por que, sin necesidad de cambiar la interfaz `RulesBussinesGateway`.

---

## Archivos a modificar

| Archivo | Cambio |
|---|---|
| `ProcessingResultCodes.java` | Agregar `SIZE_EXCEEDED`, `PATTERN_MISMATCH`, `DECOMPRESSION_ERROR` |
| `RulesBussinesService.java` | Lanzar `ProcessingException` en vez de `Mono.empty()` |
| `FileUploadRequest.java` | Agregar campo `docId` (Long) |
| `AbstractDocumentProcessingUseCase.java` | Agregar `DocumentHistoryRepository`, metodos de trace, `switchIfEmpty`->`onErrorResume`, propagar `docId` en `buildFileUploadRequest` |
| `S3GatewayAdapter.java` | Inyectar `DocumentHistoryRepository`, `doBeforeRetry` en `retryWhen` |
| `SoapGatewayAdapter.java` | Inyectar `DocumentHistoryRepository`, `doBeforeRetry` en `retryWhen` |
| `S3DocumentProcessingUseCase.java` | Agregar parametro al constructor |
| `SoapDocumentProcessingUseCase.java` | Agregar parametro al constructor |
| `DomainConfig.java` | Pasar `DocumentHistoryRepository` a todos los constructores |
| `S3DocumentProcessingUseCaseTest.java` | Actualizar constructor en tests |
| `SoapDocumentProcessingUseCaseTest.java` | Actualizar constructor en tests |

**No se crean archivos nuevos.**

---

## Modificacion: ProcessingResultCodes

**Path**: `src/main/java/com/example/fileprocessor/domain/usecase/ProcessingResultCodes.java`

Agregar constantes:
```java
public static final String SIZE_EXCEEDED = "SIZE_EXCEEDED";
public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";
public static final String DECOMPRESSION_ERROR = "DECOMPRESSION_ERROR";
```

Se elimina `BUSINESS_RULE_SKIP` (reemplazado por los codigos especificos).

---

## Modificacion: RulesBussinesService

**Path**: `src/main/java/com/example/fileprocessor/domain/service/RulesBussinesService.java`

Cambiar `Mono.empty()` por `Mono.error(new ProcessingException(...))` con codigo y mensaje descriptivo:

```java
public Mono<ProductDocumentHistory> validate(ProductDocumentHistory doc, boolean includeSizeCheck) {
    return Mono.defer(() -> {
        if (includeSizeCheck && maxFileSizeBytes != null && doc.size() != null && doc.size() > maxFileSizeBytes) {
            return Mono.error(new ProcessingException(
                ProcessingResultCodes.SIZE_EXCEEDED,
                String.format("Size %,d bytes exceeds max %,d bytes for file '%s'",
                    doc.size(), maxFileSizeBytes, doc.filename())));
        }
        if (filenamePattern != null && !filenamePattern.matcher(doc.name()).matches()) {
            return Mono.error(new ProcessingException(
                ProcessingResultCodes.PATTERN_MISMATCH,
                String.format("Filename '%s' does not match pattern '%s'",
                    doc.name(), filenamePattern.pattern())));
        }
        return Mono.just(doc);
    });
}
```

Esto permite que la traza registre `SIZE_EXCEEDED` o `PATTERN_MISMATCH` con el detalle exacto en `errorMessage`.

---

## Modificacion: AbstractDocumentProcessingUseCase

**Path**: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

### 1. Agregar 4ta dependencia

```java
private final DocumentHistoryRepository historyRepository;
```

Constructor: 4 parametros (limite exacto).

### 2. Agregar metodos privados de trace

```java
// ── Shared save ──────────────────────────────────────────────────

private void saveTrace(DocumentHistory history) {
    historyRepository.save(history)
        .doOnError(e -> log.log(Level.WARNING, "Failed to record trace: {0}", e.getMessage()))
        .subscribe();
}

// ── Trace methods (3-4 params each) ──────────────────────────────

private void traceSkip(Document doc, String filename, ProcessingException error) {
    saveTrace(DocumentHistory.builder()
        .documentId(doc.id()).filename(filename).operation(implementationName())
        .result(DocumentStatus.FAILURE.name())
        .errorCode(error.getErrorCode())
        .errorMessage(error.getMessage())
        .retry(0)
        .startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).createdAt(LocalDateTime.now())
        .build());
}

private void traceSuccess(Long docId, String filename, String operation) {
    saveTrace(DocumentHistory.builder()
        .documentId(docId).filename(filename).operation(operation)
        .result(DocumentStatus.SUCCESS.name())
        .retry(0)
        .startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).createdAt(LocalDateTime.now())
        .build());
}

private void traceFailure(Long docId, String filename, String operation, Throwable error) {
    historyRepository.findLastAudit(docId, operation)
        .defaultIfEmpty(DocumentHistory.builder().retry(0).build())
        .map(last -> last.retry() != null ? last.retry() + 1 : 1)
        .map(retry -> buildErrorHistory(docId, filename, operation, error, retry))
        .flatMap(historyRepository::save)
        .doOnError(e -> log.log(Level.WARNING, "Failed to record failure trace: {0}", e.getMessage()))
        .subscribe();
}

private DocumentHistory buildErrorHistory(
        Long docId, String filename, String operation, Throwable error, int retry) {
    String errorCode = error instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    return DocumentHistory.builder()
        .documentId(docId).filename(filename).operation(operation)
        .result(DocumentStatus.FAILURE.name())
        .errorCode(errorCode)
        .errorMessage(error.getMessage())
        .stackTrace(buildStackTrace(error))
        .retry(retry)
        .startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).createdAt(LocalDateTime.now())
        .build();
}
```

### 3. Actualizar handlers existentes

**skipDocByBussines**: cambiar firma a `(Document doc, ProcessingException error, String filename)`:
```java
private Mono<ProductDocumentHistory> skipDocByBussines(Document doc, ProcessingException error, String filename) {
    log.log(Level.INFO, "Document {0} skipped: {1}", new Object[]{doc.documentId(), error.getMessage()});
    traceSkip(doc, filename, error);
    documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
    return Mono.empty();
}
```

**handleSuccess**: cambiar firma a `(Document doc, FileUploadResult result, String filename)` y agregar al inicio:
```java
traceSuccess(doc.id(), filename, implementationName());
```

**handleError**: cambiar firma a `(Document doc, Throwable error, String filename)` y agregar al inicio:
```java
traceFailure(doc.id(), filename, implementationName(), error);
```

**startProcessing -> onErrorResume**: agregar antes del `updateStateById(PENDING)`:
```java
traceFailure(doc.id(), doc.name(), implementationName(), error);
```

### 4. Actualizar decompressIfNeeded para tracear fallos de descompresion

```java
private Flux<ProductDocumentHistory> decompressIfNeeded(ProductDocumentHistory file) {
    if (!file.isZip() || file.filename() == null || file.filename().isBlank()) {
        return Flux.just(file);
    }
    return ZipDecompressor.decompress(file)
        .onErrorMap(error -> new ProcessingException(
            ProcessingResultCodes.DECOMPRESSION_ERROR,
            "Failed to decompress ZIP '" + file.filename() + "': " + error.getMessage(),
            error));
}
```

### 5. Actualizar processDocument: switchIfEmpty -> onErrorResume

```java
.flatMap(validated -> documentValidator.validate(validated, true)
    .onErrorResume(ProcessingException.class,
        error -> skipDocByBussines(doc, error, validated.filename()))
    .flatMap(v -> uploadDocument(v, doc.productId())
        .flatMap(result -> handleSuccess(doc, result, v.filename()))
        .onErrorResume(error -> handleError(doc, error, v.filename()))
    )
);
```

El cambio clave: `.switchIfEmpty(Mono.defer(() -> skipDocByBussines(doc, validated.filename())))` se reemplaza por `.onErrorResume(ProcessingException.class, error -> skipDocByBussines(doc, error, validated.filename()))`. Esto captura la `ProcessingException` lanzada por el validador con el codigo y mensaje especifico de la regla que aplico.

### 6. Trazabilidad de reintentos en los gateways

Los reintentos ya existen en los gateways via `.retryWhen(Retry.max(3))`. Para que cada reintento quede en `historico_documentos`, se agrega `doBeforeRetry` dentro del gateway adapter para tracear cada intento fallido.

**Ejemplo en `S3GatewayAdapter`:**
```java
public Mono<FileUploadResult> send(FileUploadRequest request) {
    return Mono.fromFuture(s3AsyncClient.putObject(putRequest, AsyncResponseTransformer.toBytes()))
        .map(this::toSuccessResult)
        .retryWhen(Retry.max(MAX_RETRIES)
            .doBeforeRetry(retrySignal ->
                traceRetry(request, retrySignal)
            ))
        .onErrorMap(this::toProcessingException);
}
```

**Ejemplo en `SoapGatewayAdapter`:** mismo patron, `doBeforeRetry` dentro del `retryWhen` existente.

Cada gateway adapter necesita acceso a trazabilidad. Opciones:
- El adapter inyecta `DocumentHistoryRepository` directamente
- O se pasa un callback `Consumer<RetrySignal>` al metodo `send()`

**Enfoque elegido: callback desde el caso de uso.** La interfaz del gateway no cambia estructuralmente, pero `uploadDocument` en la subclase recibe el contexto necesario y pasa un callback al gateway:

```java
// En S3DocumentProcessingUseCase.uploadDocument:
return s3Gateway.send(request, retrySignal ->
    traceRetryAttempt(docId, filename, "S3", retrySignal.failure(), retrySignal.totalRetries() + 1)
);
```

Esto requiere agregar un parametro `Consumer<RetrySignal>` al metodo `send()` de `S3Gateway` y `SoapGateway`. Es un cambio minimo en las interfaces.

Alternativa mas simple si no se quiere modificar las interfaces del gateway: inyectar `DocumentHistoryRepository` directamente en los adapters. El adapter hardcodea el `operation` ("S3" o "SOAP") y extrae `documentId` y `filename` del `FileUploadRequest` que recibe. La traza se hace en el `doBeforeRetry` del adapter sin que el caso de uso intervenga.

**Enfoque final recomendado: adapter inyecta `DocumentHistoryRepository`.**
- Ventaja: zero cambios en interfaces de gateway, zero cambios en subclases
- El adapter usa `request.documentId()` y `request.filename()` del `FileUploadRequest`
- El `operation` es fijo por adapter ("S3", "SOAP")

```java
// En S3GatewayAdapter:
private void traceRetry(FileUploadRequest request, Retry.RetrySignal signal) {
    int attempt = (int) signal.totalRetries() + 1;
    String errorCode = signal.failure() instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    historyRepository.save(DocumentHistory.builder()
        .documentId(request.documentId())    // del FileUploadRequest
        .filename(request.filename())
        .operation("S3")
        .result(DocumentStatus.FAILURE.name())
        .errorCode(errorCode)
        .errorMessage(signal.failure().getMessage())
        .retry(attempt)
        .startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).createdAt(LocalDateTime.now())
        .build())
        .doOnError(e -> log.log(Level.WARNING, "Failed to record retry trace: {0}", e.getMessage()))
        .subscribe();
}
```

**Importante**: `FileUploadRequest.documentId` es el ID de negocio (string), no el `documentos.id` (Long). Para la FK en `historico_documentos.documento_id` se necesita el ID numerico. Si no se tiene en el request, hay dos opciones:
1. Agregar el `docId` (Long) al `FileUploadRequest` en `buildFileUploadRequest()`
2. O el adapter resuelve el ID via un query al `DocumentRepository`

Opcion 1 es la mas limpia: `buildFileUploadRequest` ya tiene acceso al `doc.id()`.

```java
private static String buildStackTrace(Throwable error) {
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement element : error.getStackTrace()) {
        sb.append(element.toString()).append('\n');
    }
    return sb.toString();
}
```

---

## Modificacion: Subclases

**S3DocumentProcessingUseCase**: agregar `DocumentHistoryRepository historyRepository` al constructor y pasar a `super()`. El metodo `uploadDocument` **no cambia** — el `.onErrorResume(this::handleUploadError)` se mantiene (convierte errores del gateway en `FileUploadResult` con status FAILURE). Los reintentos se tracean dentro del adapter.

**SoapDocumentProcessingUseCase**: igual, agregar parametro al constructor y pasar a `super()`. Sin cambios en `uploadDocument`.

---

## Modificacion: FileUploadRequest y buildFileUploadRequest

Para que el gateway adapter pueda guardar la traza con el `documento_id` correcto (FK a `documentos.id`), se agrega el campo `docId` (Long) a `FileUploadRequest` y `buildFileUploadRequest` lo propaga:

```java
// FileUploadRequest: agregar campo
private final Long docId;

// AbstractDocumentProcessingUseCase.buildFileUploadRequest: agregar
.docId(doc.id())
```

Esto permite al adapter usar `request.docId()` en el `DocumentHistory.builder().documentId(request.docId())`.

---

## Modificacion: DomainConfig

**Path**: `src/main/java/com/example/fileprocessor/application/service/config/DomainConfig.java`

Actualizar `soapDocumentUseCase` y `s3DocumentUseCase` para recibir `DocumentHistoryRepository` y pasarlo al constructor. No se requiere bean nuevo.

---

## Modificacion: Tests

Ambos tests ya tienen `@Mock DocumentHistoryRepository historyRepository`. Solo hay que verificar que el constructor reciba el mock en la posicion correcta. Agregar `lenient().when(historyRepository.save(any())).thenReturn(Mono.empty())` y `lenient().when(historyRepository.findLastAudit(anyLong(), anyString())).thenReturn(Mono.empty())` para que los traces fire-and-forget no interfieran.

---

## Mapeo de campos por tipo de evento

| Evento | errorCode | errorMessage | retry | ¿Reintenta? |
|---|---|---|---|---|
| Skip por tamano excedido | `SIZE_EXCEEDED` | "Size 15MB exceeds max 10MB for file 'doc.pdf'" | 0 | No (-> PROCESSED) |
| Skip por patron de filename | `PATTERN_MISMATCH` | "Filename 'doc.pdf' does not match pattern '.*\\.xml'" | 0 | No (-> PROCESSED) |
| Fallo al descomprimir ZIP | `DECOMPRESSION_ERROR` | "Failed to decompress ZIP 'file.zip': ..." | last+1 | Si (-> PENDING) |
| Intento fallido de upload (retry) | del `ProcessingException` o `UNKNOWN_ERROR` | error.getMessage() | last+1 | Si (retryWhen) |
| Exito upload | null | null | 0 | No (-> PROCESSED) |
| Fallo final upload (max retries) | del `ProcessingException` o `UNKNOWN_ERROR` | error.getMessage() | last+1 | No (-> FAILED) |
| Error inesperado | del `ProcessingException` o `UNKNOWN_ERROR` | error.getMessage() | last+1 | Si (-> PENDING) |

Todos los eventos usan `result = FAILURE` (excepto exito que usa `SUCCESS`) y `timestamps = LocalDateTime.now()`.

---

## Resiliencia

`traceSkip` y `traceSuccess` usan el metodo compartido:
```java
private void saveTrace(DocumentHistory history) {
    historyRepository.save(history)
        .doOnError(e -> log.log(Level.WARNING, "..."))
        .subscribe();
}
```

`traceFailure` tiene su propia cadena reactiva (`findLastAudit` -> `buildErrorHistory` -> `save`) con el mismo patron de seguridad al final.

En todos los casos:
- `.subscribe()` fire-and-forget
- `.doOnError()` captura fallos sin propagarlos al pipeline principal
- Si la DB falla al guardar la traza, solo se loguea, el procesamiento sigue

---

## Secuencia de implementacion

1. Agregar `SIZE_EXCEEDED`, `PATTERN_MISMATCH`, `DECOMPRESSION_ERROR` en `ProcessingResultCodes.java`
2. Modificar `RulesBussinesService.java` (lanzar `ProcessingException` en vez de `Mono.empty()`)
3. Agregar `docId` a `FileUploadRequest.java`
4. Modificar `AbstractDocumentProcessingUseCase.java` (trazabilidad + propagar docId)
5. Modificar `S3GatewayAdapter.java` (inyectar `DocumentHistoryRepository` + `doBeforeRetry`)
6. Modificar `SoapGatewayAdapter.java` (mismo patron)
7. Modificar `S3DocumentProcessingUseCase.java` (constructor)
8. Modificar `SoapDocumentProcessingUseCase.java` (constructor)
9. Modificar `DomainConfig.java` (wiring)
10. Actualizar tests
11. `./gradlew compileJava test`

---

## Verificacion

1. `./gradlew compileJava` -- compilacion sin errores
2. `./gradlew test` -- tests existentes pasan
3. Documento excede `maxFileSizeBytes` -> `historico_documentos` tiene `codigo_error = SIZE_EXCEEDED`
4. Documento no coincide con `filenamePattern` -> `historico_documentos` tiene `codigo_error = PATTERN_MISMATCH`
5. Fallo al descomprimir ZIP -> `codigo_error = DECOMPRESSION_ERROR`, documento vuelve a `PENDING`
6. Upload falla en intento 1, gateway reintenta -> `historico_documentos` tiene registro del intento fallido con `reintentos = 1`
7. Upload falla en intento 2 -> `historico_documentos` tiene registro con `reintentos = 2`
8. Upload exitoso en intento 3 -> `historico_documentos` tiene registro con `resultado = SUCCESS`
9. Upload falla todos los intentos -> `reintentos = 3`, documento en `FAILED`
10. Error inesperado -> documento vuelve a `PENDING` + trace guardado
11. Fallo del trace (DB caida) -> solo se loguea, NO detiene el pipeline
