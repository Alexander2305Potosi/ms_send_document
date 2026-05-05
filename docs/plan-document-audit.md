# Plan: Traza de Estados y Auditoria de Envio de Documentos

## Context

Ya existe una tabla `historico_documentos` para auditoria de envio de documentos. Se auditara sobre la infraestructura existente y se ampliaran los campos existentes.

**Decisiones tomadas:**

1. **Un registro por evento**: Cada operacion genera exactamente un registro en `historico_documentos`. No hay multiples registros por documento en una misma ejecucion.
2. **Resume solo para PENDING**: La logica de `canResume` aplica unicamente a documentos en estado `PENDING` con historial previo. Documentos en otros estados no se reprocesan.
3. **`state` es el unico campo de estado**: Se elimina `status` (antes `resultado`). El campo `state` (`estado`) es la unica fuente de verdad para el estado del documento. Los valores posibles son: `PENDING`, `IN_PROGRESS`, `PROCESSED`, `FAILED`, `SYNCED`.
4. **Deduplicacion en `findByState`**: Como los nuevos registros de auditoria incluyen `state`, `findByState` puede retornar multiples registros con el mismo `documentId`. `executePendingDocuments` agrupa por `documentId` y toma solo el registro mas reciente de cada grupo.

---

## Paso 1: Nueva migracion `002_alter_historico_documentos.sql`

Crear archivo `docs/migrations/002_alter_historico_documentos.sql`:

```sql
-- Eliminar columna redundante resultado (status)
ALTER TABLE historico_documentos
    DROP COLUMN IF EXISTS resultado;

-- Asegurar que estado (state) existe
ALTER TABLE historico_documentos
    ADD COLUMN IF NOT EXISTS estado VARCHAR(100);
```

---

## Paso 2: Modificar `DocumentHistoryEntity.java`

**Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentHistoryEntity.java`

Eliminar el campo `status` y su anotacion `@Column("resultado")`:

```java
// ELIMINAR:
@Column("resultado")
private String status;

// MANTENER:
@Column("estado")
private String state;
```

---

## Paso 3: Modificar `DocumentHistory.java` (domain record)

**Path:** `src/main/java/com/example/fileprocessor/domain/entity/DocumentHistory.java`

Eliminar `status` del record:

```java
@Builder
public record DocumentHistory(
    Long id,
    String documentId,
    String productId,
    Boolean active,
    String docKey,
    String name,
    String owner,
    String path,
    String state,
    String versionContract,
    String errorMessage,
    Boolean isZip,
    String parentZipName,
    String useCase,
    String errorCode,
    Integer retry,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

---

## Paso 4: Modificar `DocumentHistoryMapper.java`

**Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/DocumentHistoryMapper.java`

Eliminar las lineas que mapean `status`:

```java
public static DocumentHistoryEntity toEntity(DocumentHistory domain) {
    return DocumentHistoryEntity.builder()
        .documentId(domain.documentId())
        .productId(domain.productId())
        .active(domain.active() != null ? domain.active() : true)
        .docKey(domain.docKey())
        .name(domain.name())
        .owner(domain.owner())
        .path(domain.path())
        .state(domain.state())
        .versionContract(domain.versionContract())
        .errorMessage(domain.errorMessage())
        .isZip(domain.isZip() != null ? domain.isZip() : false)
        .parentZipName(domain.parentZipName())
        .useCase(domain.useCase())
        // ELIMINAR: .status(domain.status())
        .errorCode(domain.errorCode())
        .retry(domain.retry() != null ? domain.retry() : 0)
        .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
        .updatedAt(domain.updatedAt() != null ? domain.updatedAt() : LocalDateTime.now())
        .build();
}

public static DocumentHistory toDomain(DocumentHistoryEntity entity) {
    return DocumentHistory.builder()
        .id(entity.getId())
        .documentId(entity.getDocumentId())
        .productId(entity.getProductId())
        .active(entity.getActive())
        .docKey(entity.getDocKey())
        .name(entity.getName())
        .owner(entity.getOwner())
        .path(entity.getPath())
        .state(entity.getState())
        .versionContract(entity.getVersionContract())
        .errorMessage(entity.getErrorMessage())
        .isZip(entity.getIsZip())
        .parentZipName(entity.getParentZipName())
        .useCase(entity.getUseCase())
        // ELIMINAR: .status(entity.getStatus())
        .errorCode(entity.getErrorCode())
        .retry(entity.getRetry())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
}
```

---

## Paso 5: Modificar `DocumentHistoryRepository.java` (Spring Data)

**Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentHistoryRepository.java`

Agregar metodo para encontrar el ultimo registro de auditoria:

```java
public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {
    Flux<DocumentHistoryEntity> findByDocumentId(String documentId);
    Flux<DocumentHistoryEntity> findByDocumentIdAndUseCase(String documentId, String useCase);
    Flux<DocumentHistoryEntity> findByState(String state);
    Mono<DocumentHistoryEntity> findFirstByDocumentIdOrderByCreatedAtDesc(String documentId);  // NUEVO
}
```

---

## Paso 6: Modificar `DocumentHistoryR2dbcAdapter.java`

**Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentHistoryR2dbcAdapter.java`

Agregar `findLastAudit` y simplificar `updateState` (eliminar el filtro `useCase == null` que ya no aplica porque los registros de sync ahora tendran `useCase = "SYNC"`):

```java
@Override
public Mono<Void> updateState(String documentId, String state, String errorMessage) {
    return springDataRepository.findByDocumentId(documentId)
        .next()
        .flatMap(entity -> {
            entity.setState(state);
            entity.setErrorMessage(errorMessage);
            entity.setUpdatedAt(java.time.LocalDateTime.now());
            return springDataRepository.save(entity);
        })
        .then();
}

@Override
public Mono<DocumentHistory> findLastAudit(String documentId) {
    return springDataRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
        .map(DocumentHistoryMapper::toDomain);
}
```

---

## Paso 7: Modificar `DocumentHistoryRepository.java` (domain port)

**Path:** `src/main/java/com/example/fileprocessor/domain/port/out/DocumentHistoryRepository.java`

Agregar metodo `findLastAudit`:

```java
public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Flux<DocumentHistory> findByDocumentId(String documentId);
    Flux<DocumentHistory> findByState(String state);
    Mono<Void> updateState(String documentId, String state, String errorMessage);
    Mono<Integer> getRetryCount(String documentId, String useCase);
    Mono<DocumentHistory> findLastAudit(String documentId);           // NUEVO
}
```

---

## Paso 8: Modificar `AbstractDocumentProcessingUseCase`

**Path:** `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

**Modificar flujo:** `executePendingDocuments()` ahora:
1. Agrupa por `documentId` y toma solo el registro mas reciente de cada grupo (deduplicacion)
2. Verifica `canResume` antes de procesar

```java
public Flux<FileUploadResult> executePendingDocuments() {
    return historyRepository.findByState(ProductState.PENDING)
        .groupBy(DocumentHistory::documentId)
        .flatMap(group -> group.reduce((latest, current) ->
            latest.createdAt() != null && current.createdAt() != null
                && latest.createdAt().isAfter(current.createdAt()) ? latest : current))
        .flatMap(doc -> {
            String documentId = doc.documentId();
            return canResume(documentId)
                .flatMapMany(resumeable -> {
                    if (!resumeable) {
                        log.log(Level.INFO, "Document {0} already processed, skipping", documentId);
                        return Flux.empty();
                    }
                    historyRepository.updateState(documentId, ProductState.IN_PROGRESS, null).subscribe();
                    return processDocument(doc);
                });
        })
        .doOnTerminate(() -> log.log(Level.INFO, "Pipeline {0} completed", new Object[]{implementationName()}))
        .doOnError(e -> log.log(Level.SEVERE, "Pipeline error: {0}", new Object[]{e.getMessage()}))
        .doOnCancel(() -> log.log(Level.WARNING, "Pipeline {0} cancelled", new Object[]{implementationName()}));
}

private Mono<Boolean> canResume(String documentId) {
    return historyRepository.findLastAudit(documentId)
        .map(lastAudit -> {
            if (lastAudit == null) return true; // nunca procesado
            // Solo se resume si el ultimo estado no fue PROCESSED
            return !ProductState.PROCESSED.equals(lastAudit.state());
        })
        .defaultIfEmpty(true);
}

private Flux<FileUploadResult> processDocument(DocumentHistory doc) {
    String documentId = doc.documentId();
    return productRestGateway.getDocument(doc.productId(), documentId)
        .map(file -> toProductDocument(file))
        .flatMapMany(file -> {
            String filename = file.filename();
            if (!file.isZip() || filename == null || filename.isBlank()) {
                return Flux.just(file);
            }
            return ZipDecompressor.decompress(file);
        })
        .flatMap(validated -> documentValidator.validate(validated, true)
            .switchIfEmpty(Mono.defer(() -> {
                log.log(Level.INFO, "Document {0} skipped by size validation", documentId);
                historyRepository.updateState(documentId, ProductState.PROCESSED, null).subscribe();
                return Mono.empty();
            })))
        .flatMap(validated -> uploadDocument(validated, doc.productId()))
        .flatMap(result -> handleUploadSuccess(doc, result))
        .onErrorResume(error -> handleUploadError(doc, error));
}
```

---

## Paso 9: Modificar `handleUploadSuccess`

Eliminar `.status(DocumentStatus.SUCCESS.name())` y usar `.state(ProductState.PROCESSED)`:

```java
private Mono<FileUploadResult> handleUploadSuccess(DocumentHistory doc, FileUploadResult result) {
    DocumentHistory history = DocumentHistory.builder()
        .documentId(doc.documentId())
        .productId(doc.productId())
        .useCase(implementationName())
        .state(ProductState.PROCESSED)
        .retry(0)
        .createdAt(LocalDateTime.now())
        .build();
    historyRepository.save(history).subscribe();

    historyRepository.updateState(doc.documentId(), ProductState.PROCESSED, null).subscribe();
    return Mono.just(result);
}
```

---

## Paso 10: Modificar `handleUploadError`

Eliminar `.status(DocumentStatus.FAILURE.name())` y usar `.state(newState)`. Extraer el calculo de `retry` a variable local para reutilizarlo:

```java
private Mono<FileUploadResult> handleUploadError(DocumentHistory doc, Throwable error) {
    String errorCode = error instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    String errorMsg = error.getMessage();

    return historyRepository.getRetryCount(doc.documentId(), implementationName())
        .defaultIfEmpty(0)
        .flatMap(currentRetry -> {
            int retry = currentRetry + 1;
            String newState = retry >= MAX_RETRIES ? ProductState.FAILED : ProductState.PENDING;

            DocumentHistory history = DocumentHistory.builder()
                .documentId(doc.documentId())
                .productId(doc.productId())
                .useCase(implementationName())
                .state(newState)
                .errorCode(errorCode)
                .errorMessage(errorMsg)
                .retry(retry)
                .createdAt(LocalDateTime.now())
                .build();
            historyRepository.save(history).subscribe();

            historyRepository.updateState(doc.documentId(), newState, errorMsg).subscribe();
            return handleUploadError(error);
        });
}
```

---

## Paso 11: Modificar `SyncDocumentsUseCase`

**Path:** `src/main/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCase.java`

Agregar `useCase`, `retry`, `createdAt` al builder:

```java
private Mono<Void> saveDocument(ProductDocumentHistory doc) {
    String zipName = doc.isZip() ? doc.filename() : null;
    DocumentHistory history = DocumentHistory.builder()
        .documentId(doc.documentId())
        .productId(doc.productId())
        .name(doc.filename())
        .owner(doc.productId())
        .useCase("SYNC")
        .state(ProductState.SYNCED)
        .isZip(doc.isZip())
        .parentZipName(zipName)
        .retry(0)
        .createdAt(LocalDateTime.now())
        .build();
    return historyRepository.save(history);
}
```

---

## Resumen de Cambios por Archivo

| Archivo | Cambio |
|---------|--------|
| `docs/migrations/002_alter_historico_documentos.sql` | NUEVO: ALTER TABLE eliminar `resultado`, asegurar `estado` |
| `domain/entity/DocumentHistory.java` | Eliminar campo `status` |
| `infrastructure/.../entity/DocumentHistoryEntity.java` | Eliminar campo `status` y anotacion `@Column("resultado")` |
| `infrastructure/.../mapper/DocumentHistoryMapper.java` | Eliminar mapeo de `status` en `toEntity` y `toDomain` |
| `infrastructure/.../repository/DocumentHistoryRepository.java` | Agregar `findFirstByDocumentIdOrderByCreatedAtDesc` |
| `infrastructure/.../DocumentHistoryR2dbcAdapter.java` | Implementar `findLastAudit`, simplificar `updateState` |
| `domain/port/out/DocumentHistoryRepository.java` | Agregar metodo `findLastAudit` |
| `domain/usecase/AbstractDocumentProcessingUseCase.java` | Agregar `canResume()`, deduplicar por `documentId`, quitar `.status()` |
| `domain/usecase/SyncDocumentsUseCase.java` | Agregar `.useCase("SYNC")`, `.retry(0)`, `.createdAt(...)` |

---

## Flujo Simplificado

```
GET /api/v1/products?processor=soap
  └─ executePendingDocuments()
       ├─ findByState(PENDING) → todos los registros PENDING
       ├─ groupBy(documentId) + reduce → solo el mas reciente por documento
       ├─ canResume(documentId) → verificar ultimo estado
       │    └─ Si PROCESSED → saltar (Flux.empty())
       │    └─ Si null/PENDING/FAILED → procesar
       ├─ updateState(IN_PROGRESS)
       ├─ productRestGateway.getDocument()
       ├─ ZipDecompressor.decompress() (si aplica)
       ├─ documentValidator.validate()
       ├─ uploadDocument()
       │    ├─ SUCCESS → state=PROCESSED
       │    └─ ERROR   → state=PENDING (si retry < 3) o FAILED (si retry >= 3)
       └─ historico_documentos:
            { state=PROCESSED/PENDING/FAILED, errorCode=..., retry=N }
```

---

## Escenarios de Fallo

| Escenario | State final | Historico |
|-----------|-------------|-----------|
| Fallo en descompresion ZIP | `PENDING` | 1 registro con `state=PENDING` |
| Fallo en validacion (size) | `PROCESSED` | 1 registro con `state=PROCESSED` |
| Fallo en homologacion | `PENDING` | 1 registro con `state=PENDING` |
| Fallo en SOAP (timeout) | `PENDING` o `FAILED` | 1 registro por intento |
| Max retries agotados (3) | `FAILED` | 1 registro final con `state=FAILED` |
| Exito | `PROCESSED` | 1 registro con `state=PROCESSED` |

---

## canResume — Logica Simplificada

Solo se procesan documentos PENDING si:

1. No existe historial → procesar
2. Ultimo state no es `PROCESSED` → procesar
3. Ultimo state es `PROCESSED` → saltar

```java
private Mono<Boolean> canResume(String documentId) {
    return historyRepository.findLastAudit(documentId)
        .map(lastAudit -> {
            if (lastAudit == null) return true;
            return !ProductState.PROCESSED.equals(lastAudit.state());
        })
        .defaultIfEmpty(true);
}
```

---

## Preguntas Cerradas

1. **`state` unico campo de estado**: Se elimina `status` — `state` es la unica fuente de verdad
2. **Un registro por evento**: Un solo registro final por ejecucion
3. **canResume para PENDING**: Aplica solo a documentos PENDING, verifica ultimo `state`
4. **Deduplicacion por `documentId`**: `executePendingDocuments` agrupa y toma el registro mas reciente por documento para evitar procesamiento duplicado
