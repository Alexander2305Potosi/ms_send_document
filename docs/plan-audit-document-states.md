# Plan: Auditoría y Trazabilidad de Estados de Documentos

---

## Fase 1: Correcciones Críticas

### 1.1 Corregir recursión infinita en `handleUploadError`

**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

**Problema actual (líneas 89-103):**
```java
private Mono<FileUploadResult> handleUploadError(DocumentHistory doc, Throwable error) {
    String errorCode = error instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    String errorMsg = error.getMessage();

    return historyRepository.findLastAudit(doc.documentId())
        .defaultIfEmpty(DocumentHistory.builder().retry(0).build())
        .flatMap(current -> {
            int retry = current.retry() != null ? current.retry() + 1 : 1;
            String newState = retry >= MAX_RETRIES ? ProductState.FAILED : ProductState.PENDING;

            historyRepository.updateWithAudit(doc.documentId(), newState, errorCode, errorMsg, retry, implementationName()).subscribe();
            return handleUploadError(error);  // ← RECURSIÓN INFINITA
        });
}
```

**Solución:**
```java
private Mono<FileUploadResult> handleUploadError(DocumentHistory doc, Throwable error) {
    String errorCode = error instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    String errorMsg = error.getMessage();

    return historyRepository.findLastAudit(doc.documentId())
        .defaultIfEmpty(DocumentHistory.builder().retry(0).build())
        .flatMap(current -> {
            int retry = current.retry() != null ? current.retry() + 1 : 1;
            String newState = retry >= MAX_RETRIES ? ProductState.FAILED : ProductState.PENDING;

            historyRepository.updateWithAudit(doc.documentId(), newState, errorCode, errorMsg, retry, implementationName()).subscribe();
            
            return Mono.just(FileUploadResult.builder()
                .status(DocumentStatus.FAILURE.name())
                .errorCode(errorCode)
                .errorMessage(errorMsg)
                .processedAt(Instant.now())
                .success(false)
                .retryCount(retry)
                .build());
        });
}
```

---

### 1.2 Implementar deduplicación en `executePendingDocuments`

**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

**Problema actual (línea 35):**
`findByState(PENDING)` retorna TODOS los registros, incluyendo registros antiguos del mismo documento.

**Solución:**
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
```

**Nota:** El método `canResume` también debe recibir `useCase` como parámetro para filtrar correctamente.

---

## Fase 2: Mejoras de Trazabilidad

### 2.1 Agregar filtro por `useCase` en `findLastAudit`

#### 2.1.1 Domain Port

**Archivo:** `src/main/java/com/example/fileprocessor/domain/port/out/DocumentHistoryRepository.java`

**Cambio:** Agregar nueva firma:
```java
Mono<DocumentHistory> findLastAudit(String documentId);
Mono<DocumentHistory> findLastAudit(String documentId, String useCase);  // NUEVA
```

---

#### 2.1.2 Spring Data Repository

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentHistoryRepository.java`

**Cambio:** Agregar método:
```java
Mono<DocumentHistoryEntity> findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc(String documentId, String useCase);
```

---

#### 2.1.3 Adapter

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentHistoryR2dbcAdapter.java`

**Cambio:** Implementar nueva firma:
```java
@Override
public Mono<DocumentHistory> findLastAudit(String documentId, String useCase) {
    return springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc(documentId, useCase)
        .map(DocumentHistoryMapper::toDomain);
}
```

---

### 2.2 Agregar campos de auditoría extendida

#### 2.2.1 Domain Record

**Archivo:** `src/main/java/com/example/fileprocessor/domain/entity/DocumentHistory.java`

**Nuevos campos:**
```java
String operation;        // FETCH, DECOMPRESS, VALIDATE, UPLOAD
String messageId;        // Header "message-id" del endpoint = traceId
String stackTrace;       // Stack trace completo del error
LocalDateTime startedAt;  // Timestamp inicio de operación
LocalDateTime completedAt; // Timestamp fin de operación
```

---

#### 2.2.2 Entity

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentHistoryEntity.java`

**Nuevas propiedades:**
```java
@Column("operacion")
private String operation;

@Column("message_id")
private String messageId;

@Column("stack_trace")
private String stackTrace;

@Column("fecha_inicio")
private LocalDateTime startedAt;

@Column("fecha_fin")
private LocalDateTime completedAt;
```

---

#### 2.2.3 Mapper

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/DocumentHistoryMapper.java`

**En `toEntity`:**
```java
.operation(domain.operation())
.messageId(domain.messageId())
.stackTrace(domain.stackTrace())
.startedAt(domain.startedAt())
.completedAt(domain.completedAt())
```

**En `toDomain`:**
```java
.operation(entity.getOperation())
.messageId(entity.getMessageId())
.stackTrace(entity.getStackTrace())
.startedAt(entity.getStartedAt())
.completedAt(entity.getCompletedAt())
```

---

#### 2.2.4 Migración SQL

**Archivo:** `docs/migrations/003_audit_fields.sql`

```sql
ALTER TABLE historico_documentos 
    ADD COLUMN IF NOT EXISTS operacion VARCHAR(50),
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS stack_trace TEXT,
    ADD COLUMN IF NOT EXISTS fecha_inicio TIMESTAMP,
    ADD COLUMN IF NOT EXISTS fecha_fin TIMESTAMP;
```

---

## Resumen de Archivos a Modificar

| # | Archivo | Cambios |
|---|---------|---------|
| 1 | `AbstractDocumentProcessingUseCase.java` | Fix recursión, deduplicación |
| 2 | `DocumentHistoryRepository.java` (domain port) | + `findLastAudit(documentId, useCase)` |
| 3 | `DocumentHistoryRepository.java` (Spring Data) | + `findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc` |
| 4 | `DocumentHistoryR2dbcAdapter.java` | Implementar `findLastAudit(documentId, useCase)` |
| 5 | `DocumentHistory.java` | + `operation`, `messageId`, `stackTrace`, `startedAt`, `completedAt` |
| 6 | `DocumentHistoryEntity.java` | + columnas `operacion`, `message_id`, `stack_trace`, `fecha_inicio`, `fecha_fin` |
| 7 | `DocumentHistoryMapper.java` | Mapear 5 campos nuevos |
| 8 | `docs/migrations/003_audit_fields.sql` | Nueva migración |

---

## Campos Nuevos en la Tabla

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `operacion` | VARCHAR(50) | FETCH, DECOMPRESS, VALIDATE, UPLOAD |
| `message_id` | VARCHAR(100) | Header "message-id" del endpoint |
| `stack_trace` | TEXT | Stack trace completo del error |
| `fecha_inicio` | TIMESTAMP | Inicio de la operación |
| `fecha_fin` | TIMESTAMP | Fin de la operación |

---

## Queries para Debugging

```sql
-- Historial completo de un documento
SELECT id, id_documento, estado, operacion, codigo_error, 
       mensaje_error, reintentos, caso_uso, message_id, 
       fecha_creacion, fecha_inicio, fecha_fin
FROM historico_documentos 
WHERE id_documento = ?
ORDER BY fecha_creacion DESC;

-- Buscar por message-id
SELECT * FROM historico_documentos 
WHERE message_id = ?;

-- Estadísticas de errores
SELECT codigo_error, COUNT(*) as total, MAX(fecha_creacion) as ultimo
FROM historico_documentos 
WHERE estado = 'FAILED' 
GROUP BY codigo_error 
ORDER BY total DESC;

-- Documentos con stack trace
SELECT id_documento, estado, codigo_error, stack_trace 
FROM historico_documentos 
WHERE stack_trace IS NOT NULL;
```

---

## Estados y Transiciones

```
PENDING ──→ IN_PROGRESS ──→ PROCESSED (éxito)
                ↑
                └──→ PENDING (error, retry < 3) ──→ ... ──→ FAILED (retry >= 3)
                         │
                         └──→ FAILED (error fatal)
```

## Escenarios de Fallo con Trazabilidad

| Escenario | Estado Final | messageId | stackTrace | Campos capturados |
|-----------|-------------|-----------|------------|-------------------|
| Timeout en SOAP | PENDING/FAILED | ✓ | ✓ | ✓ |
| Base64 inválido | PENDING | ✓ | ✓ | ✓ |
| ZIP corrupto | PENDING | ✓ | ✓ | ✓ |
| S3 500 | PENDING | ✓ | ✓ | ✓ |
| Validación size | PROCESSED | ✓ | - | ✓ |

---

## Flujo Después de los Cambios

```
executePendingDocuments()
├── findByState(PENDING)
├── groupBy(documentId) + reduce → solo más reciente
├── canResume(documentId, useCase) → verifica último estado por useCase
├── updateState(IN_PROGRESS)
├── processDocument()
│   ├── GET document (con messageId del header)
│   ├── ZipDecompressor.decompress()
│   ├── documentValidator.validate()
│   └── uploadDocument()
│       ├── SUCCESS → state=PROCESSED, messageId guardado
│       └── ERROR   → state=PENDING (retry<3) o FAILED (retry>=3), messageId guardado
└── Historico: { documentId, state, errorCode, retry, useCase, messageId, operation, stackTrace, startedAt, completedAt }
```