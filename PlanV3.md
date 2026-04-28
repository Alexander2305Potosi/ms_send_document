# Plan V3 — Refactorización basada en Template Method Pattern

**Rama:** `feature/v3.0`  
**Base:** `feature/v2.0`  
**Fecha:** 2026-04-27  
**Objetivo:** Refactorizar la arquitectura de procesamiento de documentos aplicando el patrón Template Method, asegurando resiliencia con checkpointing y reintentos.

---

## 1. Diagnóstico del Estado Actual

### 1.1 Arquitectura actual (rama `feature/v2.0`)

```
ProductHandler
  └─> DocumentProcessingOrchestrator  (SOAP o S3, vía Spring @Profile)
       └─> DocumentProcessingPipeline  (5 etapas secuenciales con flatMap)
            ├── 1. validateBusinessRules  → DocumentSkipHandler
            ├── 2. fileValidator.validate → FileValidator
            ├── 3. buildRequest          → DocumentSendRequest
            ├── 4. sendWithCircuitBreaker → ResilienceOperator + DocumentSender
            │                                └─> DocumentSenderImpl
            │                                     └─> FileGateway
            │                                          ├── SoapGatewayAdapter
            │                                          └── S3GatewayAdapter (@Profile("s3"))
            └── 5. updateStatuses        → DB + ProductStatusAggregator
```

**Problemas identificados:**

| # | Problema | Impacto |
|---|----------|---------|
| P1 | `DocumentProcessingPipeline` es una clase concreta única, pero se instancia 2 veces (SOAP y S3) con dependencias casi idénticas. La diferencia real entre SOAP y S3 reside solo en `DocumentValidationRules` y `FileValidator`. | Duplicación de configuración en `DomainConfig.java` (131 líneas para 2 pipelines casi idénticos). |
| P2 | `DocumentProcessingOrchestrator` es un wrapper superfluo (60 líneas) que solo añade `implementationName` para logging. La orquestación podría residir en la clase base. | Indirección innecesaria. |
| P3 | `DocumentValidationRules.shouldSkipFolder()` usa `String.contains()` para excluir carpetas. No soporta regex ni patrones flexibles. | No permite exclusiones por formato de nombre de carpeta (ej: `.*_backup$`, `temp/.*`). |
| P4 | `DocumentSenderImpl` no registra `CommunicationLog` en caso de fallo, solo en éxito. | Trazabilidad incompleta: no se sabe cuándo ni por qué falló un envío. |
| P5 | No existe checkpointing: si el proceso falla tras enviar un documento pero antes de actualizar su status, no hay forma de saber qué ocurrió. | Inconsistencia potencial entre el estado real (documento enviado) y el estado registrado en BD. |
| P6 | `SoapGatewayAdapter` tiene lógica de reintentos (`retryWhen`), pero `S3GatewayAdapter` no. La resiliencia no es uniforme. | Comportamiento asimétrico entre implementaciones. |
| P7 | `CommunicationLog` solo se guarda en éxito (`saveSuccessLog`), perdiendo trazabilidad de fallos y reintentos. | Sin historial completo, el debugging post-mortem es imposible. |

### 1.1.0 Análisis y mejoras por problema

#### P1 — Pipeline instanciado 2 veces con dependencias casi idénticas

**Causa raíz:** `DocumentProcessingPipeline` es una clase concreta pensada para ser instanciada con parámetros distintos (`FileValidator`, `DocumentValidationRules`, `ResilienceOperator`), pero el 80% de su lógica (orquestación, envío, post-procesamiento, logging) es idéntica. La instanciación múltiple en `DomainConfig` (131 líneas) es un síntoma, no la enfermedad.

**Solución con Template Method:** ya definida en el plan — `AbstractDocumentProcessingUseCase` centraliza lo compartido, subclases implementan solo lo diferente.

**Mejora adicional — Selector de gateway sin @Profile:**

El uso de `@Profile("s3")` impide ejecutar ambos procesadores simultáneamente en la misma JVM. Esto es una limitación si en el futuro se requiere enrutar documentos a distintos gateways según reglas de negocio (ej: según `contentType`, `origin` o tamaño).

Alternativa: eliminar `@Profile` y usar un `GatewaySelector`:

```java
@Component
public class GatewaySelector {

    private final Map<String, FileGateway> gateways;

    public GatewaySelector(List<FileGateway> gatewayList) {
        this.gateways = gatewayList.stream()
            .collect(Collectors.toMap(
                gw -> gw.gatewayName(),  // "S3", "SOAP"
                Function.identity()));
    }

    public FileGateway select(ProductDocumentToProcess doc) {
        // Regla: ZIP > 50MB → S3, documentos individuales → SOAP
        if (doc.getContentType() != null
                && doc.getContentType().contains("zip")
                && doc.getContent().length > 50 * 1024 * 1024) {
            return gateways.get("S3");
        }
        return gateways.getOrDefault(
            doc.getCorrelationId() != null ? "S3" : "SOAP",
            gateways.get("SOAP"));
    }
}
```

`FileGateway` gana un método `default String gatewayName()`.

**Impacto:** Permite migraciones progresivas SOAP → S3 sin cambiar el perfil activo, solo modificando reglas de routing.

---

#### P2 — `DocumentProcessingOrchestrator` es un wrapper superfluo

**Causa raíz:** La separación Orchestrator/Pipeline se introdujo para desacoplar "encontrar documentos" de "procesar un documento". En la práctica, el Orchestrator quedó en 60 líneas porque:
- `findPendingDocuments()` es una línea
- `claimDocument()` es una línea
- El `maxConcurrency` es hardcoded a 10
- `implementationName` solo sirve para el log

**Solución con Template Method:** fusión directa en `AbstractDocumentProcessingUseCase`.

**Mejora adicional — `maxConcurrency` configurable por procesador:**

No todos los gateways soportan la misma concurrencia. SOAP puede saturarse con 10; S3 puede manejar 50 o más.

En `application.yml`:
```yaml
app:
  processors:
    soap:
      max-concurrency: 5      # NUEVO
    s3:
      max-concurrency: 50     # NUEVO
```

En `AbstractDocumentProcessingUseCase`:
```java
protected int maxConcurrency() {
    return processorSettings.getMaxConcurrency(); // default 10
}
```

---

#### P3 — `String.contains()` para exclusión de carpetas

**Causa raíz:** `DocumentValidationRules` se diseñó con listas simples de strings. Evolucionar a regex requirió crear `FolderExclusionRegexConfig` como Value Object separado para no romper la interfaz `FileValidationConfig`.

**Mejora adicional — Validación fail-fast al arrancar:**

Si un patrón regex es inválido, la app debe fallar al iniciar, no al procesar el primer documento:

```java
public class FolderExclusionRegexConfig {

    private final List<Pattern> exclusionPatterns;
    private final List<String> rawPatterns;

    public FolderExclusionRegexConfig(List<String> regexPatterns) {
        this.rawPatterns = List.copyOf(regexPatterns);
        List<Pattern> compiled = new java.util.ArrayList<>();
        for (String pattern : regexPatterns) {
            try {
                compiled.add(Pattern.compile(pattern));
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalStateException(
                    "Invalid folder exclusion regex pattern: '" + pattern
                    + "' at position " + compiled.size() + ". " + e.getMessage(), e);
            }
        }
        this.exclusionPatterns = List.copyOf(compiled);
    }
}
```

Spring crea el bean al iniciar → si una regex es inválida, `ApplicationContext` no levanta.

**Mejora adicional — Soporte para regex con grupos nombrados:**

Permite extraer metadata de la ruta para usarla en el routing:

```yaml
folder-exclusion-regex:
  - ".*/(?<client>[a-z]+)/archive/.*"   # excluye archivos en carpetas archive por cliente
```

---

#### P4 y P7 — `CommunicationLog` incompleto (solo éxito) + sin trazabilidad de fallos

**Causa raíz única:** `DocumentSenderImpl.send()` hace `fileGateway.send() → flatMap(saveSuccessLog)`. El `flatMap` solo se ejecuta en éxito (la naturaleza de `flatMap` en Reactor es que no se invoca si el upstream emite error). No hay `doOnError` ni `onErrorResume` para el log de fallos.

**Solución unificada (P4 + P7):** `saveCommunicationLog()` en la clase abstracta captura **todos** los outcomes:

```java
protected Mono<FileUploadResult> sendWithResilience(DocumentSendRequest request) {
    Instant start = Instant.now();

    return Mono.defer(() -> {
        @SuppressWarnings("unchecked")
        Mono<FileUploadResult> decorated = (Mono<FileUploadResult>) resilienceOperator.decorate(
            fileGateway.send(request), request.getTraceId());

        return decorated
            .flatMap(result -> saveCommunicationLog(request, result, 0, start)
                .thenReturn(result))
            .onErrorResume(error -> {
                // Propaga el error pero ANTES guarda el log de fallo
                int retries = extractRetries(error);
                String errorCode = extractErrorCode(error);
                return saveCommunicationLog(request,
                    buildFailureResult(errorCode, request.getTraceId()),
                    retries, start)
                    .then(Mono.error(error));
            });
    });
}
```

**Mejora adicional — `CommunicationLog` con metadatos de latencia y gateway:**

```java
public class CommunicationLog {
    // ... campos actuales existentes ...
    private final Long latencyMs;        // NUEVO: tiempo del gateway en ms
    private final String gatewayName;    // NUEVO: "S3" o "SOAP"
    private final String metadata;       // NUEVO: JSON con detalles (S3 key, SOAP endpoint, HTTP status)
}
```

Esto permite construir dashboards: latencia p50/p99 por gateway, tasa de error por tipo.

---

#### P5 — Inconsistencia Gateway vs BD

Ya detallado en sección 1.1.1.

**Mejora — Recovery inline integrado en el pipeline:**

Durante `executePendingDocuments()`, el método `claimDocumentWithRecovery()` (ver P8) ya verifica documentos en `PROCESSING` con timestamp vencido (`processed_at < :staleTime`) y los reclama atómicamente. Esto ocurre como parte normal del pipeline existente, sin endpoints nuevos ni jobs programados. Si un operador detecta documentos atascados revisando logs/BD, simplemente invoca el endpoint ya existente `POST /api/v1/products/process` para lanzar un nuevo ciclo de procesamiento.

---

#### P6 — Resiliencia asimétrica (SOAP con retryWhen, S3 sin él)

**Causa raíz:** `SoapGatewayAdapter` implementó reintentos inline (`retryWhen` + `Retry.backoff`) porque HTTP a SOAP es inherentemente inestable. `S3GatewayAdapter` no lo hizo porque el SDK de AWS tiene su propio retry interno. Pero el SDK retry es opaco (no se registra en `CommunicationLog`).

**Mejora — Extraer reintento del Gateway hacia el `ResilienceOperator`:**

El principio: el Gateway hace **exactamente un intento**. Toda la lógica de reintento reside en la capa de dominio (`ResilienceOperator` o la clase abstracta). Esto:

1. Hace uniforme la resiliencia (misma política para S3 y SOAP)
2. Permite registrar CADA reintento en `CommunicationLog`
3. Simplifica los gateways (menos código, menos responsabilidades)

```java
// ResilientOperator — NUEVA interfaz con reintentos
public interface ResilienceOperator {
    <T> Mono<T> decorate(Mono<T> source, String operationName);
    <T> Mono<T> decorateWithRetry(Mono<T> source, String operationName, int maxRetries);
}

// En AbstractDocumentProcessingUseCase:
protected Mono<FileUploadResult> sendWithResilience(DocumentSendRequest request) {
    return resilienceOperator.decorateWithRetry(
        fileGateway.send(request),
        request.getTraceId(),
        retryConfig.maxAttempts() // configurable por procesador
    );
}
```

Los gateways (`SoapGatewayAdapter`, `S3GatewayAdapter`) quedan reducidos a:
- `Mono<FileUploadResult> send(DocumentSendRequest request)` — un solo intento, sin `retryWhen()`

---

### 1.1.1 Nuevos problemas descubiertos durante el análisis

| # | Problema | Impacto |
|---|----------|---------|
| P8 | `claimDocument()` no es atómicamente seguro a nivel de BD. Usa un patrón "update + select" que en R2DBC puede tener race condition entre el UPDATE y el SELECT subsiguiente si dos nodos compiten por el mismo documento. | Bajo hoy (mono-nodo), Alto mañana (multi-nodo). Dos pods pueden procesar el mismo documento simultáneamente por una ventana de microsegundos. |
| P9 | No existe `idempotency-key` en los requests hacia los gateways. Si el gateway SoapGatewayAdapter reintenta tras un timeout, el sistema destino puede recibir la misma petición 2 veces sin saber que es un duplicado. | Duplicados silenciosos en el sistema externo. El `CommunicationLog` registrará 1 éxito pero el sistema destino tendrá 2 entidades. |
| P10 | `LoadProductsUseCase` usa `Base64.getDecoder().decode()` inline en lugar de delegar a `Base64Utils.decode()`. No hay validación de que el contenido sea Base64 válido antes de guardarlo en BD. | Documento corrupto guardado en BD sin detección temprana. El error aparece tarde, durante el envío al gateway. |
| P11 | No hay mecanismo de `graceful shutdown` para detener el procesamiento. Si se hace `kill -15`, los documentos en vuelo (entre gateway y checkpoint) se pierden. | Misma inconsistencia de P5 pero amplificada en deploys frecuentes. |

---

#### P8 — `claimDocument`: race condition, documentos huérfanos y reclaim seguro

##### Diagnóstico de la situación actual

```java
// ProductDocumentRepository — implementación inferida del comportamiento actual:
Mono<Boolean> claimDocument(String documentId) {
    return databaseClient.sql("""
        UPDATE product_document
        SET status = 'PROCESSING', trace_id = :traceId, processed_at = :now
        WHERE document_id = :id AND status = 'PENDING'
        """)
        .bind("id", documentId)
        .bind("traceId", UUID.randomUUID().toString())
        .bind("now", Instant.now())
        .fetch()
        .rowsUpdated()
        .map(rows -> rows > 0);
}
```

**Análisis de atomicidad real:**

En PostgreSQL/R2DBC, un solo `UPDATE ... WHERE status = 'PENDING'` es atómico: la base de datos toma un row lock sobre la fila seleccionada. Si dos nodos ejecutan el mismo UPDATE concurrentemente, PostgreSQL serializa ambas operaciones. La primera actualiza la fila (`rowsUpdated=1`), la segunda ve que `status` ya no es `'PENDING'` (`rowsUpdated=0`). No hay race condition en el UPDATE en sí.

**El verdadero problema es otro — tres escenarios de fallo:**

```
Escenario A: Crash post-UPDATE, pre-SELECT
──────────────────────────────────────────
Node A:  UPDATE ... SET status='PROCESSING'  → 1 fila ✓
Node A:  [CRASH aquí]
         SELECT nunca se ejecuta
Resultado: documento en PROCESSING, NADIE lo procesa. Huérfano permanente.


Escenario B: Documento atascado tras reintentos fallidos
──────────────────────────────────────────────────────────
Node A:  claimDocument → PROCESSING
         gateway.send() → TIMEOUT (3 reintentos agotados)
         onErrorResume → ¿qué pasa aquí?
         
En el código actual de SoapGatewayAdapter.onErrorResume:
- TimeoutException → Mono.error(SoapCommunicationException)
- Esto rompe el pipeline → DocumentProcessingPipeline.updateStatuses NO se ejecuta
Resultado: documento en PROCESSING, error registrado en log pero BD sigue PROCESSING.


Escenario C: TraceId idéntico en reclaim
───────────────────────────────────────────
Si se implementa reclaim de documentos atascados, el traceId original
se pierde. No hay forma de correlacionar el intento original con el reclaim.
```

##### Solución propuesta — `claimDocumentWithRecovery` atómico

Fusionar la lógica de **claim** y **reclaim** en una sola operación atómica que reconoce documentos atascados:

```java
// ProductDocumentRepository — nuevo método unificado:
Mono<ClaimResult> claimDocumentWithRecovery(String documentId, Duration staleThreshold) {
    Instant staleTime = Instant.now().minus(staleThreshold);

    return databaseClient.sql("""
        UPDATE product_document
        SET status = :newStatus,
            trace_id = :traceId,
            processed_at = :now,
            processing_instance_id = :instanceId,
            claim_attempts = claim_attempts + 1
        WHERE document_id = :id
          AND (
              status = 'PENDING'
              OR (status = 'PROCESSING' AND processed_at < :staleTime)
              OR (status = 'RETRY')
          )
        """)
        .bind("id", documentId)
        .bind("newStatus", "PROCESSING")
        .bind("traceId", UUID.randomUUID().toString())
        .bind("now", Instant.now())
        .bind("instanceId", instanceId)
        .bind("staleTime", staleTime)
        .fetch()
        .rowsUpdated()
        .flatMap(rows -> {
            if (rows > 0) {
                // SELECT el documento actualizado para obtener el status previo
                return findById(documentId)
                    .map(doc -> new ClaimResult(true, doc.getStatus(), doc.getClaimAttempts()));
            }
            return Mono.just(ClaimResult.NOT_CLAIMED);
        });
}
```

```java
/**
 * Resultado de una operación de claim.
 *
 * @param claimed        true si este nodo reclamó el documento
 * @param previousStatus estado antes del claim (PENDING, PROCESSING, RETRY)
 * @param attemptCount   número total de intentos de claim (1 = primer intento)
 */
public record ClaimResult(boolean claimed, String previousStatus, int attemptCount) {
    public static final ClaimResult NOT_CLAIMED = new ClaimResult(false, null, 0);

    /**
     * Indica si el claim fue un RECOVERY (el documento estaba atascado en PROCESSING).
     */
    public boolean isRecovery() {
        return "PROCESSING".equals(previousStatus) || "RETRY".equals(previousStatus);
    }
}
```

**Nuevos campos requeridos en BD:**

```sql
ALTER TABLE product_document ADD COLUMN IF NOT EXISTS
    processing_instance_id VARCHAR(64);   -- identifica qué pod reclamó el documento
ALTER TABLE product_document ADD COLUMN IF NOT EXISTS
    claim_attempts INT DEFAULT 0;         -- cuántos claims ha tenido el documento
ALTER TABLE product_document ADD COLUMN IF NOT EXISTS
    version INT DEFAULT 0;                -- optimistic locking para futuros casos de uso
```

**Integración en el Template Method:**

```java
protected final Mono<FileUploadResult> processDocument(
        ProductDocumentToProcess pending, String traceId) {

    return documentRepository.claimDocumentWithRecovery(
            pending.getDocumentId(), Duration.ofMinutes(staleThresholdMinutes))
        .filter(ClaimResult::claimed)
        .flatMap(claim -> {
            if (claim.isRecovery()) {
                log.warn("Document {} recovered from {} state (attempt #{})",
                    pending.getDocumentId(), claim.previousStatus(), claim.attemptCount());
                // Si estaba en PROCESSING, verificar si ya fue enviado al gateway
                // consultando CommunicationLog antes de reenviar
                return logRepository.findLatestByDocumentId(pending.getDocumentId())
                    .flatMap(lastLog -> {
                        if ("SUCCESS".equals(lastLog.getStatus())) {
                            log.info("Document {} already sent (found in log) — reconciling status",
                                pending.getDocumentId());
                            return reconcileAlreadySent(pending, lastLog, traceId);
                        }
                        return executeFullPipeline(pending, traceId);
                    })
                    .switchIfEmpty(executeFullPipeline(pending, traceId));
            }
            // Primer claim — pipeline normal
            return executeFullPipeline(pending, traceId);
        })
        .switchIfEmpty(Mono.fromRunnable(() ->
            log.debug("Document {} claimed by another instance", pending.getDocumentId())));
}
```

Este diseño resuelve tres problemas simultáneamente:
1. **Atomicidad real**: el UPDATE condicional con triple `OR` es una sola operación en BD
2. **Recovery automático**: documentos atascados en `PROCESSING` > N minutos son reclamados y reconcialiados con `CommunicationLog`
3. **Trazabilidad**: `processing_instance_id` y `claim_attempts` permiten auditoría completa de quién procesó qué y cuántos intentos tomó

---

#### P9 — `idempotency-key`: estrategia por tipo de gateway

##### El problema real no es solo añadir un campo

Hay 3 escenarios distintos donde ocurre duplicación, y cada uno requiere distinta estrategia:

| Escenario | Causa | Ocurre en | Solución real |
|-----------|-------|-----------|---------------|
| **A: Timeout + Retry** | El gateway respondió OK, pero la red perdió la respuesta. El cliente reintenta. | `SoapGatewayAdapter.retryWhen()` | `Idempotency-Key` HTTP header — el servidor SOAP DEBE soportarlo |
| **B: Crash post-envío** | Pod muere después de `gateway.send()` pero antes de `checkpoint()`. Al reiniciar, `executePendingDocuments()` reclama el documento atascado. | `AbstractDocumentProcessingUseCase.processDocument()` | Consultar `CommunicationLog` ANTES de reenviar (ver P8 recovery) |
| **C: S3 doble PUT** | Idéntico a B. Al reiniciar, el documento atascado se re-envía a S3 con nueva key (nuevo UUID). | S3 | No es duplicado real (keys distintas), pero genera objeto huérfano. Mitigado con P5 checkpoint. |

##### Solución para Escenario A — Idempotency Key determinista

La clave de idempotencia debe ser **determinista**: mismo `documentId + traceId` = misma key en cada reintento del mismo intento de procesamiento.

```java
public class IdempotencyKey {
    private final String value;

    private IdempotencyKey(String documentId, String traceId, int attempt) {
        // Formato: doc-{documentId}-{traceId}-attempt-{attempt}
        // Ejemplo: doc-abc123-def456-ghi-789-attempt-2
        this.value = String.format("doc-%s-%s-attempt-%d", documentId, traceId, attempt);
    }

    public static IdempotencyKey forFirstAttempt(String documentId, String traceId) {
        return new IdempotencyKey(documentId, traceId, 1);
    }

    public IdempotencyKey nextAttempt() {
        int current = Integer.parseInt(value.substring(value.lastIndexOf('-') + 1));
        return new IdempotencyKey(
            extractDocId(value), extractTraceId(value), current + 1);
    }

    public String value() { return value; }

    // Método de fábrica para recovery (cuando se pierde el traceId original)
    public static IdempotencyKey forRecovery(String documentId, String newTraceId) {
        // Incluye marcador "recovery" para que el sistema destino sepa que es un retry
        return new IdempotencyKey(documentId, newTraceId, 0); // attempt 0 = recovery
    }
}
```

##### Inclusión en `DocumentSendRequest`:

```java
public record DocumentSendRequest(
    String documentId,
    byte[] fileContent,
    String filename,
    String contentType,
    long fileSize,
    String traceId,
    String parentFolder,
    String childFolder,
    String idempotencyKey       // NUEVO: obligatorio, generado en buildRequest()
) {
    // Factory method para construir request con idempotency key
    public static DocumentSendRequest create(
            ProductDocumentToProcess doc, String traceId,
            FolderInfo folderInfo, IdempotencyKey key) {
        return new DocumentSendRequest(
            doc.getDocumentId(),
            doc.getContent(),
            doc.getFilename(),
            doc.getContentType(),
            doc.getContent() != null ? doc.getContent().length : 0,
            traceId,
            folderInfo.parentFolder(),
            folderInfo.childFolder(),
            key.value()
        );
    }
}
```

##### Gateways — cómo usan la idempotency key:

**S3 — Metadata de objeto:**

```java
// S3GatewayAdapter.send():
PutObjectRequest putRequest = PutObjectRequest.builder()
    .bucket(s3Properties.bucketName())
    .key(buildKey(request))
    .contentType(request.getContentType())
    .metadata(Map.of(
        "idempotency-key", request.getIdempotencyKey(),
        "trace-id", request.getTraceId(),
        "original-filename", request.getFilename(),
        "document-id", request.getDocumentId()
    ))
    .build();
```

No previene la duplicación (S3 no deduplica por metadata), pero permite **identificar y limpiar objetos huérfanos** con un Lambda programado.

**SOAP — Header HTTP personalizado:**

```java
// SoapGatewayAdapter.send():
webClient.post()
    .header("Idempotency-Key", request.getIdempotencyKey())
    .header("X-Document-Id", request.getDocumentId())
    // ...
```

El sistema SOAP destino debe: (a) indexar `Idempotency-Key`, (b) si ya existe una respuesta para ese key → devolverla sin re-procesar.

##### Fallback cuando el sistema destino NO soporta idempotency:

En `preValidate()` de la clase abstracta, antes de enviar, verificar si ya existe un `CommunicationLog` de éxito para ese `documentId`:

```java
protected Mono<ProductDocumentToProcess> preValidate(
        ProductDocumentToProcess pending, String traceId) {

    // Si el documento viene de un recovery (claim.isRecovery()),
    // verificar si ya se envió exitosamente en el intento anterior
    return logRepository.findLatestSuccessByDocumentId(pending.getDocumentId())
        .flatMap(lastSuccess -> {
            log.warn("Document {} already SUCCESS in previous attempt (traceId={})"
                + " — reconciling status, skip resend",
                pending.getDocumentId(), lastSuccess.getTraceId());
            // Saltar directamente a checkpoint con el resultado anterior
            return Mono.just(pending); // ← se marca con un flag interno
        })
        .switchIfEmpty(Mono.empty()) // no hay success previo → continuar pipeline
        .thenReturn(pending); // HACK: esto no es correcto reactivamente
}
```

**Mejor — `ProcessingDecision` como resultado de preValidate:**

```java
/**
 * Decisión tomada durante la pre-validación.
 *
 * @param action CONTINUE (procesar normalmente) o SKIP_COMPLETED (ya fue enviado, reconciliar)
 * @param reason razón de la decisión (para logging)
 */
public record ProcessingDecision(String action, String reason) {
    public boolean shouldSkip() {
        return "SKIP_COMPLETED".equals(action);
    }

    public static ProcessingDecision CONTINUE = new ProcessingDecision("CONTINUE", "");
    public static ProcessingDecision skipCompleted(String reason) {
        return new ProcessingDecision("SKIP_COMPLETED", reason);
    }
}
```

---

#### P10 — Validación Base64: detección temprana en cascada

##### Mapeo del flujo real donde aparece el problema

```
LoadProductsUseCase.execute()
  └─> ProductRestGatewayAdapter.getAllProducts(traceId)
       └─> productGateway.getDocument(productId, docInfo, traceId)
            │
            ├─> response JSON → ProductDocumentInfo
            ├─> String content = info.getContent()          // el Base64 string
            ├─> Base64.getDecoder().decode(content)          // ← AQUÍ explota
            │    └─> IllegalArgumentException("Illegal base64 character ...")
            │         SIN filename, SIN documentId, SIN productId
            │
            └─> La excepción revienta todo el Flux de carga.
                Si 1 documento de 100 tiene Base64 corrupto:
                → error irrecuperable
                → los otros 99 documentos no se cargan
                → producto queda en estado parcial
```

##### Solución — Pipeline de carga tolerante a fallos por documento

El principio: un documento corrupto no debe detener la carga de los demás.

```java
// Base64Utils.java — método seguro con contexto:
public static byte[] decodeSafe(String encoded, String filename, String documentId) {
    if (encoded == null || encoded.isBlank()) {
        throw new DomainException(
            "Empty Base64 content for document: " + filename + " (documentId=" + documentId + ")",
            ProcessingResultCodes.EMPTY_CONTENT);
    }
    try {
        return Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
        throw new DomainException(
            "Invalid Base64 content for document: " + filename
            + " (documentId=" + documentId + "): " + e.getMessage(),
            ProcessingResultCodes.INVALID_BASE64, e);
    }
}

// Sobrecarga para byte[] (contenido ya decodificado):
public static boolean isValidBase64Utf8(byte[] decoded, String filename) {
    try {
        new String(decoded, StandardCharsets.UTF_8);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

##### Validación en cascada — 3 niveles:

```
Nivel 1: ProductRestGatewayAdapter  (fuente externa → domain)
  ├── ¿String vacío/nulo?  → EMPTY_CONTENT
  ├── ¿Formato Base64 válido? → decodeSafe()
  └── ¿Decodificado > 0 bytes? → DomainException(EMPTY_CONTENT)

Nivel 2: LoadProductsUseCase  (domain → persistence)
  ├── ¿content.length > maxFileSizeMb * 1024 * 1024?
  │    → Documento marcado como FAILURE, producto continúa
  ├── ¿filename tiene extensión permitida?
  │    → Documento marcado como SKIPPED
  └── ¿ZipArchive corrupto?
       → Documento ZIP marcado como FAILURE, hijos no se generan

Nivel 3: DocumentValidationRules (persistence → processing)
  ├── Validación de extensión (FileValidator)
  ├── Validación de filename (path traversal)
  └── Validación de tamaño (maxSize bytes)
```

##### `LoadProductsUseCase` con error handling por documento:

```java
public Flux<LoadProductsResult> execute() {
    return productGateway.getAllProducts(traceId)
        .flatMap(product -> loadProductDocuments(product, traceId)
            .collectList()
            .map(documentResults -> buildProductResult(product, documentResults))
            .onErrorResume(error -> {
                log.error("Failed to load product {}: {}", product.getProductId(), error.getMessage());
                return Mono.just(LoadProductsResult.builder()
                    .productId(product.getProductId())
                    .name(product.getName())
                    .status("FAILURE")
                    .message("Product load failed: " + error.getMessage())
                    .traceId(traceId)
                    .processedAt(Instant.now())
                    .success(false)
                    .build());
            }), maxConcurrency);
}

private Flux<LoadProductsResult> loadProductDocuments(ProductInfo product, String traceId) {
    return Flux.fromIterable(product.getDocuments())
        .flatMap(docInfo -> productGateway.getDocument(product.getProductId(), docInfo, traceId)
            .flatMap(doc -> processDocumentSafely(product, doc, traceId))
            .onErrorResume(DomainException.class, e -> handleInvalidDocument(product, docInfo, traceId, e))
            .onErrorResume(Exception.class, e -> handleUnknownDocumentError(product, docInfo, traceId, e)),
            MAX_CONCURRENT_DOCUMENTS);
}

private Mono<LoadProductsResult> processDocumentSafely(ProductInfo product, ProductDocumentInfo doc, String traceId) {
    return Mono.fromCallable(() -> {
        // Nivel 1: validación Base64
        byte[] decodedContent = Base64Utils.decodeSafe(
            doc.getContent(), doc.getFilename(), doc.getDocumentId());

        // Nivel 2: validación de contenido mínima
        if (decodedContent.length == 0) {
            throw new DomainException(
                "Empty content for document: " + doc.getFilename(),
                ProcessingResultCodes.EMPTY_CONTENT);
        }

        // Construir entidad de dominio
        return ProductDocumentToProcess.builder()
            .documentId(doc.getDocumentId())
            .productId(product.getProductId())
            .filename(doc.getFilename())
            .content(decodedContent)
            .contentType(doc.getContentType())
            .origin(doc.getOrigin())
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .traceId(traceId)
            .build();
    })
    .flatMap(entity -> documentRepository.save(entity)
        .thenReturn(LoadProductsResult.success(product.getProductId(), doc.getFilename(), traceId)));
}

private Mono<LoadProductsResult> handleInvalidDocument(ProductInfo product, ProductDocumentInfo docInfo,
                                                        String traceId, DomainException e) {
    log.warn("Invalid document {} for product {}: {} (code={})",
        docInfo.getFilename(), product.getProductId(), e.getMessage(), e.getErrorCode());

    // Guardar un registro de documento fallido para trazabilidad
    ProductDocumentToProcess failedDoc = ProductDocumentToProcess.builder()
        .documentId(docInfo.getDocumentId())
        .productId(product.getProductId())
        .filename(docInfo.getFilename())
        .status(DocumentStatus.FAILURE_VALUE)
        .origin(docInfo.getOrigin())
        .traceId(traceId)
        .errorCode(e.getErrorCode())
        .build();

    return documentRepository.save(failedDoc)
        .thenReturn(LoadProductsResult.failure(product.getProductId(), docInfo.getFilename(),
            traceId, e.getMessage()));
}
```

**Resultado:** si 1 documento de 100 tiene Base64 corrupto, los otros 99 se cargan normalmente. El documento corrupto se registra como `FAILURE` con `errorCode=INVALID_BASE64` y es visible en `findByProductId()` para debugging.

---

#### P11 — Graceful Shutdown: drenar sin perder trazabilidad

##### El verdadero alcance

El graceful shutdown no es solo detener `executePendingDocuments()`. Afecta a **tres** flujos reactivos simultáneos:

| Flujo | Disparador | Qué debe pasar en shutdown |
|-------|-----------|---------------------------|
| `loadProducts(ServerRequest)` | REST POST | Si está en curso, terminar el producto actual y cancelar los siguientes |
| `processPendingProducts(ServerRequest)` | REST POST | Detener nuevas lecturas de BD, pero completar documentos en `PROCESSING` |
| `executePendingDocuments()` (interno, background) | Cron / scheduler | Drenar: no tomar más docs, completar en vuelo, checkpoint final |

##### Arquitectura de shutdown progresivo

```
Tiempo:  ─── SIGTERM ───────────────────────────── SIGKILL ───>
          │                                           │
          │  terminationGracePeriodSeconds (30s)      │
          │                                           │
Fase 1:   │  Fase 2: drenado              │  Fase 3:    │
Inmediato │  (max 25s)                    │  Force      │
          │                               │  (5s)       │
          │                               │             │
Health    │  Health PROBES:               │             │
Liveness  │   → OUT_OF_SERVICE            │             │
Readiness │   → NOT_READY                 │             │
          │                               │             │
REST:     │  REST:                        │             │
- Dejar   │  - 503 en nuevas peticiones   │             │
  recibir │  - Peticiones en curso:       │             │
  tráfico │    completar (timeout 25s)    │             │
          │                               │             │
Pipeline: │  Pipeline:                    │  Pipeline:  │
- Señal   │  - takeWhile(!draining)       │  - Mono      │
  drain   │  - Completar docs en vuelo    │    pending   │
  = true  │  - Checkpoint final           │    .cancel() │
          │  - Log pipeline drained       │  - Log       │
          │                               │    force     │
          │                               │    shutdown  │
```

##### `GracefulShutdownManager` — componente central:

```java
@Component
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);

    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final Instant shutdownStartedAt = null; // se setea en onApplicationEvent

    private volatile Instant shutdownStarted;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdownStarted = Instant.now();
        draining.set(true);
        log.info("Graceful shutdown sequence started at {}", shutdownStarted);
    }

    // ===== Estado consultable =====

    public boolean isDraining() { return draining.get(); }

    public Duration elapsedSinceShutdown() {
        return shutdownStarted != null
            ? Duration.between(shutdownStarted, Instant.now())
            : Duration.ZERO;
    }

    /**
     * Indica si todavía hay tiempo para completar trabajo en curso.
     * Deja 5 segundos de margen antes del SIGKILL para cerrar conexiones.
     */
    public boolean hasRemainingTime(Duration terminationGracePeriod) {
        Duration elapsed = elapsedSinceShutdown();
        Duration deadline = terminationGracePeriod.minusSeconds(5);
        return elapsed.compareTo(deadline) < 0;
    }

    // ===== Health Indicator =====

    @Component
    public static class DrainHealthIndicator implements HealthIndicator {

        private final GracefulShutdownManager manager;

        public DrainHealthIndicator(GracefulShutdownManager manager) {
            this.manager = manager;
        }

        @Override
        public Health health() {
            if (manager.isDraining()) {
                return Health.outOfService()
                    .withDetail("draining", true)
                    .withDetail("shutdown_started_at", manager.shutdownStarted)
                    .withDetail("elapsed_seconds", manager.elapsedSinceShutdown().toSeconds())
                    .build();
            }
            return Health.up().build();
        }
    }
}
```

##### Integración con `AbstractDocumentProcessingUseCase`:

```java
public abstract class AbstractDocumentProcessingUseCase {

    private final GracefulShutdownManager shutdownManager;
    private final Duration terminationGracePeriod;
    private final Duration drainTimeout;  // típicamente terminationGracePeriod - 5s

    public final Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            // Fase 2: dejar de tomar documentos nuevos
            .takeWhile(doc -> !shutdownManager.isDraining())
            .flatMap(this::processPendingDocument, maxConcurrency())
            // Timeout controlado: si el drenado excede el deadline, cancelar
            .take(Duration.ofSeconds(drainTimeout.getSeconds()))
            .doOnTerminate(() -> {
                long pending = countDocumentsInFlight();
                log.info("Pipeline {} drained. Docs still in flight: {} (will be reclaimed by another pod)",
                    implementationName(), pending);
            })
            .doOnCancel(() -> log.warn("Pipeline {} force-cancelled due to shutdown timeout",
                implementationName()));
    }

    /**
     * Cuenta documentos en PROCESSING asignados a ESTA instancia,
     * que quedarán para recovery por otro pod.
     */
    private long countDocumentsInFlight() {
        return documentRepository.countByStatusAndInstance("PROCESSING", instanceId);
    }
}
```

##### Configuración en `application.yml`:

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 25s  # Fase 2: drenado

server:
  shutdown: graceful                 # Fase 1: no aceptar nuevas requests

app:
  shutdown:
    drain-timeout-seconds: 20        # tiempo máximo para drenar pipeline (< lifecycle.timeout)
    termination-grace-period: 30s    # debe coincidir con Kubernetes terminationGracePeriodSeconds
```

##### Kubernetes — readiness probe durante drenado:

```yaml
# deployment.yaml
spec:
  terminationGracePeriodSeconds: 30
  containers:
  - name: app
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
        scheme: HTTP
      periodSeconds: 2
      failureThreshold: 2
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
        scheme: HTTP
```

**Flujo completo durante un deploy:**

1. `kubectl apply` → Kubernetes crea nuevo pod
2. Nuevo pod pasa readiness ✅ → empieza a recibir tráfico
3. Kubernetes envía `SIGTERM` al **viejo** pod
4. `GracefulShutdownManager.onApplicationEvent(ContextClosedEvent)` → `draining = true`
5. `DrainHealthIndicator` → `/actuator/health/readiness` → `OUT_OF_SERVICE` (Kuberentes lo marca NOT READY en ~4s)
6. Pipeline: `takeWhile(!draining)` detiene nuevos documentos → documentos en vuelo terminan `checkpoint()`
7. Request REST en curso: Spring WebFlux graceful shutdown → completan o devuelven 503 tras timeout
8. Si `elapsed > 25s`: `take(Duration)` fuerza cancelación → logs de advertencia
9. `SIGKILL` a los 30s → pod muere
10. Documentos en `PROCESSING` del pod muerto → reclaim por otro pod (P8 recovery) en el siguiente ciclo

---

## 1.2 Auditoría de Calidad — Hard-coding, Parámetros, Transformaciones y Buenas Prácticas

### 1.2.1 Hard-coded — valores literales encontrados en el plan

| Lugar | Hard-code | Debe ser |
|-------|-----------|----------|
| P1 `GatewaySelector.select()` | `50 * 1024 * 1024` (50MB threshold) | `gatewaySelectionConfig.largeFileThresholdMb()` |
| P1 `GatewaySelector.select()` | `"S3"`, `"SOAP"` strings mágicos | `FileGateway.gatewayName()` (ya se usa en el Map) — correcto. Pero el fallback `"SOAP"` hardcoded debe ser una constante. |
| P2 `maxConcurrency()` | `// default 10` (comentario) | El default debe venir de `@ConfigurationProperties` con `@DefaultValue("10")` |
| P4 `saveCommunicationLog(..., 0, ...)` | `0` como retryCount | `CommunicationLog.FIRST_ATTEMPT = 0` (constante semántica) |
| P5 `claimDocumentWithRecovery()` | `Duration.ofMinutes(staleThresholdMinutes)` sin definir | `processorSettings.staleThresholdMinutes()` con default 5 |
| P8 `isRecovery()` | `"PROCESSING".equals(...)` y `"RETRY".equals(...)` | `DocumentStatus.PROCESSING_VALUE` y `DocumentStatus.RETRY_VALUE` (ya existen) |
| P8 `processDocument()` | `"SUCCESS".equals(lastLog.getStatus())` | `DocumentStatus.SUCCESS_VALUE` (ya existe) |
| P10 `loadProductDocuments()` | `MAX_CONCURRENT_DOCUMENTS` constante | `processorProperties.getMaxConcurrentDocumentLoad()` |
| P11 `hasRemainingTime()` | `minusSeconds(5)` | Propiedad `app.shutdown.safety-margin-seconds: 5` |
| P11 `executePendingDocuments()` | `"PROCESSING"` string literal | `DocumentStatus.PROCESSING_VALUE` |

**Corrección — Nuevas `@ConfigurationProperties` requeridas:**

```yaml
app:
  processors:
    soap:
      max-file-size-mb: 50
      max-concurrency: 5
      stale-threshold-minutes: 5          # NUEVO: tiempo para considerar un doc como "atascado"
      max-concurrent-document-load: 10    # NUEVO
    s3:
      max-file-size-mb: 100
      max-concurrency: 50
      stale-threshold-minutes: 5

  gateway-selection:                      # NUEVO: grupo completo
    large-file-threshold-mb: 50           # umbral para routing automático a S3
    default-gateway: "SOAP"

  shutdown:                               # NUEVO: grupo completo (ya parcialmente en el plan)
    drain-timeout-seconds: 20
    safety-margin-seconds: 5
    termination-grace-period-seconds: 30
```

---

### 1.2.2 Métodos con más de 4 parámetros — violaciones y soluciones

#### Violación #1 — Constructor de `AbstractDocumentProcessingUseCase` (8+ dependencias)

**Problema:** Las subclases `SoapDocumentProcessingUseCase` y `S3DocumentProcessingUseCase` reciben por constructor todas las dependencias compartidas + las específicas. Esto produce constructores de 8-10 parámetros.

**Solución — Parameter Object `ProcessingDependencies`:**

```java
/**
 * Agrupa TODAS las dependencias compartidas del pipeline de procesamiento.
 * Se construye una sola vez en DomainConfig y se inyecta en cada subclase.
 *
 * Principio: 1 solo parámetro compuesto en lugar de 8 parámetros planos.
 */
public record ProcessingDependencies(
    ProductDocumentRepository documentRepository,
    ProductStatusAggregator statusAggregator,
    ResilienceOperator resilienceOperator,
    FileGateway fileGateway,
    CommunicationLogRepository logRepository
) {}
```

```java
// Constructor de subclases — 3 parámetros en lugar de 8:
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final ProcessorSettings settings;

    public S3DocumentProcessingUseCase(
            ProcessingDependencies deps,            // 1: dependencias compartidas
            ProcessorSettings settings,              // 2: configuración S3
            FolderExclusionRegexConfig folderRegex   // 3: exclusión regex
    ) {
        super(deps);
        this.settings = settings;
        this.folderRegex = folderRegex;
    }
}
```

**DomainConfig simplificado:**

```java
@Bean
public ProcessingDependencies processingDependencies(
        ProductDocumentRepository dr, ProductStatusAggregator sa,
        ResilienceOperator ro, FileGateway fg, CommunicationLogRepository lr) {
    return new ProcessingDependencies(dr, sa, ro, fg, lr);
}

@Bean
public S3DocumentProcessingUseCase s3UseCase(ProcessingDependencies deps, ProcessorConfig cfg) {
    return new S3DocumentProcessingUseCase(deps, cfg.getS3(),
        new FolderExclusionRegexConfig(cfg.getS3().getFolderExclusionRegex()));
}
```

#### Violación #2 — `DocumentSendRequest` con 9 campos

**Problema:** El record `DocumentSendRequest` tiene 9 campos planos, lo que hace frágil cualquier construcción manual y propenso a invertir parámetros adyacentes (ej: `parentFolder` vs `childFolder`).

**Solución — Agrupar campos relacionados:**

```java
// Antes: 9 campos planos
public record DocumentSendRequest(
    String documentId, byte[] fileContent, String filename,
    String contentType, long fileSize, String traceId,
    String parentFolder, String childFolder, String idempotencyKey
) {}

// Después: 5 campos agrupados por cohesión semántica
public record DocumentSendRequest(
    DocumentIdentity identity,       // documentId + filename
    BinaryContent content,           // fileContent + contentType + fileSize
    String traceId,
    FolderRouting routing,           // parentFolder + childFolder
    String idempotencyKey
) {
    public record DocumentIdentity(String documentId, String filename) {}
    public record BinaryContent(byte[] data, String contentType, long sizeBytes) {
        public static BinaryContent from(byte[] data, String contentType) {
            return new BinaryContent(data, contentType, data != null ? data.length : 0);
        }
    }
    public record FolderRouting(String parentFolder, String childFolder) {
        public static FolderRouting from(FolderInfo info) {
            return new FolderRouting(info.parentFolder(), info.childFolder());
        }
    }

    // Factory: 4 parámetros (dentro del límite)
    public static DocumentSendRequest create(
            ProductDocumentToProcess doc, String traceId,
            FolderInfo folder, String idempotencyKey) {
        return new DocumentSendRequest(
            new DocumentIdentity(doc.getDocumentId(), doc.getFilename()),
            BinaryContent.from(doc.getContent(), doc.getContentType()),
            traceId,
            FolderRouting.from(folder),
            idempotencyKey
        );
    }
}
```

**Trade-off:** más tipos (clases nested), pero cada uno tiene ≤ 3 campos. La construcción es semántica: `new DocumentSendRequest(identity, content, traceId, routing, key)` — 5 parámetros, pero el factory method `create()` queda con 4.

---

### 1.2.3 Transformaciones excesivas de objetos — redundancias detectadas

#### Redundancia #1 — Doble construcción de `CommunicationLog`

**Situación:** El plan propone `saveCommunicationLog()` llamado desde éxito Y desde error, cada uno con su builder duplicado.

**Antes (plan actual — código duplicado):**

```java
// Éxito:
.flatMap(result -> saveCommunicationLog(request, result, 0, start)
    .thenReturn(result))

// Error:
return saveCommunicationLog(request,
    buildFailureResult(errorCode, request.getTraceId()),
    retries, start)
    .then(Mono.error(error));
```

**Solución — `CommunicationLogFactory` (Single Responsibility):**

```java
@Component
public class CommunicationLogFactory {

    private final String gatewayName;  // "S3" o "SOAP" — inyectado

    /**
     * Único punto de construcción de CommunicationLog.
     * Elimina builders dispersos por el código.
     */
    public CommunicationLog create(
            String documentId, String filename, String traceId,
            String status, int retryCount, String errorCode,
            Instant startTime, Map<String, Object> metadata) {

        long latencyMs = Duration.between(startTime, Instant.now()).toMillis();

        return CommunicationLog.builder()
            .traceId(traceId)
            .documentId(documentId)
            .filename(filename)
            .status(status)
            .retryCount(retryCount)
            .errorCode(errorCode)
            .createdAt(Instant.now())
            .gatewayName(gatewayName)
            .latencyMs(latencyMs)
            .metadata(toJson(metadata))
            .build();
    }

    private String toJson(Map<String, Object> map) {
        // serialización simple (Jackson disponible via Spring)
        return map.isEmpty() ? "{}" : serialize(map);
    }
}
```

#### Redundancia #2 — `ExternalServiceResponse` → `FileUploadResult` (gateway SOAP)

**Situación actual en `SoapGatewayAdapter`:**
```java
.map(response -> toFileUploadResult(response, request.getTraceId()))
```

`ExternalServiceResponse` y `FileUploadResult` comparten 6 de 7 campos. Es una transformación 1:1 innecesaria.

**Solución — Eliminar `ExternalServiceResponse`:**

`SoapMapper.fromSoapXml()` debe retornar `FileUploadResult` directamente, sin el tipo intermedio. Esto elimina `ExternalServiceResponse` del dominio (es un DTO de infraestructura innecesario).

```java
// SoapMapper.java:
public FileUploadResult fromSoapXml(String responseXml, String traceId) {
    // Parsear XML → construir FileUploadResult directamente
    // ...
    return FileUploadResult.builder()
        .status(status)
        .message(message)
        .correlationId(correlationId)
        .traceId(traceId)
        .processedAt(Instant.now())
        .externalReference(externalRef)
        .success(true) // o false según el response XML
        .build();
}

// SoapGatewayAdapter.send():
return soapCall
    .onErrorResume(...);  // ya no hay .map(toFileUploadResult)
```

#### Redundancia #3 — `ClaimResult` → `ProductDocumentToProcess` (mismo documento en ambos)

**Situación en `processDocument()`:**
```java
return documentRepository.claimDocumentWithRecovery(pending.getDocumentId(), ...)
    .filter(ClaimResult::claimed)
    .flatMap(claim -> {
        if (claim.isRecovery()) {
            // usa `pending` (el original) + `claim` (el nuevo ClaimResult)
            // Ambos se refieren al MISMO documento
        }
        return executeFullPipeline(pending, traceId); // pending pasado de nuevo
    });
```

**Solución — `ClaimedDocument` unifica ambos:**

```java
/**
 * Resultado de un claim exitoso. Contiene tanto el resultado del claim
 * como el documento ya actualizado (evita pasar el pending original por separado).
 */
public record ClaimedDocument(
    ProductDocumentToProcess document,   // documento actualizado (estado PROCESSING)
    ClaimResult claim                    // metadata del claim (isRecovery, attempts, etc.)
) {}
```

```java
protected final Mono<FileUploadResult> processDocument(
        ProductDocumentToProcess pending, String traceId) {

    return documentRepository.claimDocumentWithRecovery(
            pending.getDocumentId(), staleThreshold)
        .filter(ClaimResult::claimed)
        .flatMap(claim -> {
            ClaimedDocument claimed = new ClaimedDocument(pending, claim);

            if (claim.isRecovery()) {
                return handleRecovery(claimed, traceId);   // 2 params
            }
            return executeFullPipeline(claimed, traceId);  // 2 params
        })
        ...
}
```

#### Redundancia #4 — `LoadProductsUseCase`: doble construcción de `ProductDocumentToProcess`

En `processDocumentSafely()` y `handleInvalidDocument()` ambos construyen un `ProductDocumentToProcess` usando el builder. Solo difieren en `status`/`content`/`errorCode`.

**Solución — Método factory único en `ProductDocumentToProcess`:**

```java
@Builder
public record ProductDocumentToProcess(
    String documentId, String productId, String parentDocumentId,
    String filename, byte[] content, String contentType,
    String origin, String status, Instant createdAt,
    Instant processedAt, String traceId, String correlationId, String errorCode
) {
    // Factory para carga exitosa
    public static ProductDocumentToProcess loaded(
            String documentId, String productId, String filename,
            byte[] content, String contentType, String origin, String traceId) {
        return ProductDocumentToProcess.builder()
            .documentId(documentId).productId(productId)
            .filename(filename).content(content).contentType(contentType)
            .origin(origin).status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now()).traceId(traceId).build();
    }

    // Factory para documento fallido en carga
    public static ProductDocumentToProcess failed(
            String documentId, String productId, String filename,
            String origin, String errorCode, String traceId) {
        return ProductDocumentToProcess.builder()
            .documentId(documentId).productId(productId)
            .filename(filename).status(DocumentStatus.FAILURE_VALUE)
            .origin(origin).errorCode(errorCode).traceId(traceId).build();
    }
}
```

---

### 1.2.4 Buenas prácticas — violaciones detectadas

#### BP1 — `@EventListener` vs `ApplicationListener` duplicado

`GracefulShutdownManager` implementa `ApplicationListener<ContextClosedEvent>` pero el plan también menciona `@EventListener`. Son equivalentes — elegir uno. `@EventListener` es más limpio (anotación en método, no en interfaz).

#### BP2 — Estados como strings mágicos en múltiples lugares

`"PROCESSING"`, `"SUCCESS"`, `"FAILURE"`, `"RETRY"`, `"PENDING"` aparecen dispersos. Deben usarse **siempre** desde las constantes `DocumentStatus.*_VALUE`.

```java
// Mal — string mágica:
documentRepository.countByStatusAndInstance("PROCESSING", instanceId);

// Bien — constante:
documentRepository.countByStatusAndInstance(DocumentStatus.PROCESSING_VALUE, instanceId);
```

#### BP3 — Sin validación de propiedades al iniciar

Las `@ConfigurationProperties` actuales (`S3Properties`, `SoapProperties`) tienen `@Validated` pero los nuevos campos no tienen constraints.

```java
// ProcessorSettings — nuevos campos con validación:
@Min(1) @Max(100) 
private int maxConcurrency = 10;

@Min(1) @Max(60)
private int staleThresholdMinutes = 5;

@Min(1) @Max(100)
private int maxConcurrentDocumentLoad = 10;

// FolderExclusionRegexConfig — se valida en el constructor con fail-fast (ya implementado en P3)
```

#### BP4 — Exposición de estado interno en Health Indicator

`DrainHealthIndicator.health()` expone `shutdown_started_at` y `elapsed_seconds`. Esto es útil para debugging pero filtra detalles operativos. Evaluar si debe ir solo en `/actuator/info` o detrás de un profile `debug`.

```java
@Override
public Health health() {
    if (manager.isDraining()) {
        Health.Builder builder = Health.outOfService()
            .withDetail("draining", true);
        // Solo exponer detalles en profile debug
        if (debugProfile) {
            builder.withDetail("shutdown_started_at", manager.shutdownStarted)
                   .withDetail("elapsed_seconds", manager.elapsedSinceShutdown().toSeconds());
        }
        return builder.build();
    }
    return Health.up().build();
}
```

---

### 1.2.5 Resumen — Correcciones aplicadas al plan

| Auditoría | Hallazgos | Correcciones |
|-----------|-----------|-------------|
| Hard-coded | 11 valores literales en el plan | 7 nuevos grupos de `@ConfigurationProperties` |
| Métodos >4 params | Constructor con 8+ dependencias, `DocumentSendRequest` con 9 campos planos | `ProcessingDependencies` record + agrupación `DocumentIdentity`/`BinaryContent`/`FolderRouting` |
| Transformaciones excesivas | 4 redundancias: `CommunicationLog` x2, `ExternalServiceResponse`, `ClaimResult`+`pending`, `ProductDocumentToProcess` x2 | `CommunicationLogFactory`, eliminar `ExternalServiceResponse`, `ClaimedDocument`, factories en `ProductDocumentToProcess` |
| Buenas prácticas | 6 violaciones (subscribe sin error, cron hardcoded, strings mágicos, etc.) | `doOnError+onErrorComplete`, `@ConditionalOnProperty`, constantes `DocumentStatus.*`, validación `@Min/@Max` en properties |

---

### Tabla consolidada de problemas con soluciones completas

| # | Problema | Causa raíz | Solución | Alcance del cambio |
|---|----------|-----------|----------|--------------------|
| P1 | Duplicación DomainConfig | Pipeline sin abstracción | Template Method + `GatewaySelector` | `DomainConfig.java` (-70 líneas), 3 clases nuevas |
| P2 | Orchestrator superfluo | Separación artificial Orchestrator/Pipeline | Fusión en abstracta + `maxConcurrency` por props | Eliminar `DocumentProcessingOrchestrator` |
| P3 | `String.contains` sin regex | `DocumentValidationRules` legacy | `FolderExclusionRegexConfig` + fail-fast startup | Nuevo value object, 1 campo en `ProcessorSettings` |
| P4 | `CommunicationLog` solo éxito | `DocumentSenderImpl.saveSuccessLog()` acoplado a `flatMap` | `saveCommunicationLog` en `sendWithResilience()` con `onErrorResume` | `AbstractDocumentProcessingUseCase.sendWithResilience()` |
| P5 | Sin checkpointing | `updateStatuses` fusiona doc + producto en un paso | `checkpoint()` inmediato post-envío + recovery inline vía `claimDocumentWithRecovery()` | Método `checkpoint()` + `claimDocumentWithRecovery()` |
| P6 | Resiliencia asimétrica | Retry en `SoapGatewayAdapter`, ausente en S3 | Gateways hacen 1 intento; retry en `ResilienceOperator` | `SoapGatewayAdapter` (-30 líneas), `S3GatewayAdapter` (+2 líneas) |
| P7 | Sin trazabilidad fallos | `CommunicationLog` sin `errorCode` ni `retryCount` | Log en cada outcome con `latencyMs`, `gatewayName`, `metadata` | `CommunicationLog` (+3 campos) |
| P8 | Race condition `claimDocument` | Documentos atascados en `PROCESSING` sin recovery | `claimDocumentWithRecovery` atómico + reconcilación vía `CommunicationLog` | `ProductDocumentRepository` (nuevo método), BD (+3 columnas) |
| P9 | Sin `idempotency-key` | No hay diferenciación entre primer intento y retry | `IdempotencyKey` determinista (`documentId:traceId:attempt`) en request + `ProcessingDecision` SKIP_COMPLETED | `DocumentSendRequest` (+1 campo), 2 gateways (+metadata) |
| P10 | Sin validación Base64 temprana | `Base64.getDecoder().decode()` inline sin contexto | `decodeSafe()` con `DomainException` + pipeline tolerante a fallos por documento | `Base64Utils` (+1 método), `LoadProductsUseCase` (refactor) |
| P11 | Sin graceful shutdown | Sin señal de drain en pipelines reactivos | `GracefulShutdownManager` + `takeWhile` + `take(timeout)` + health probes | 1 clase nueva, `AbstractDocumentProcessingUseCase` (+10 líneas), deployment.yaml |

### 1.1.1 Análisis detallado de P5 — La inconsistencia Gateway vs BD

El gap ocurre entre el paso 4 (`sendWithCircuitBreaker`) y el paso 5 (`updateStatuses`) del pipeline actual:

```
DocumentProcessingPipeline.process()  (código actual en feature/v2.0)

  Paso 4: sendWithCircuitBreaker
    └─> DocumentSenderImpl.send(request)
         └─> fileGateway.send(request)
              ├── SoapGatewayAdapter: POST HTTP al endpoint SOAP → 200 OK ✓
              └── S3GatewayAdapter:  putObject a S3 → eTag recibido ✓
         └─> saveSuccessLog() → INSERT en communication_log ✓
    
    En este punto, el documento YA fue enviado al destino externo.
    Pero en BD el documento sigue en estado PROCESSING.

         ▼  SI OCURRE UN CRASH AQUÍ ▼

  Paso 5: updateStatuses  →  NUNCA se ejecuta
    ├── documentRepository.updateStatus(SUCCESS, correlationId, ...)  ✗ NO ejecutado
    └── statusAggregator.updateProductStatus(...)                     ✗ NO ejecutado
```

**Consecuencias por tipo de gateway:**

| Gateway | ¿Es idempotente el envío? | Consecuencia del crash |
|---------|--------------------------|------------------------|
| **S3** | Sí — `putObject` con la misma key y bucket sobreescribe el objeto. El contenido es idéntico. | Re-procesar crea una **versión duplicada con distinta key** (la key incluye UUID), generando objetos huérfanos en el bucket. Inconsistencia: hay N+1 objetos en S3 pero solo N documentos en BD. |
| **SOAP** | No — cada POST al endpoint SOAP crea una nueva entidad en el sistema destino. | Re-procesar crea un **registro duplicado** en el sistema externo. Inconsistencia: hay N+1 entidades en el destino pero solo N envíos registrados en BD. Peor aún: el `correlationId` del primer envío (exitoso) se pierde para siempre. |

**Causa raíz:** `updateStatuses` hace dos cosas en secuencia (actualizar documento + agregar producto), y ninguna de las dos ocurre **inmediatamente** después del envío exitoso. La operación `saveSuccessLog` sí ocurre, pero es un registro de auditoría, no una transición de estado.

**Solución en el Template Method (v3.0):**

Separar la actualización de estado del documento (crítica, debe ser inmediata) de la agregación del producto (diferible, puede reintentarse):

```
AbstractDocumentProcessingUseCase.processDocument()  (nuevo diseño)

  Paso 4: sendWithResilience(request)
    ├── fileGateway.send(request) → respuesta del gateway
    ├── saveCommunicationLog(SUCCESS o FAILURE) → auditoría completa
    └── retorna FileUploadResult
         │
         ▼
  Paso 5: checkpoint(pending, result, traceId)    ← INMEDIATO. Atómico. Mínimo.
    └── documentRepository.updateStatus(
            documentId,
            SUCCESS | FAILURE,         // estado terminal
            traceId,
            correlationId,             // se preserva incluso si hay crash después
            errorCode)
         │
         │  Si el checkpoint falla → Mono.error → el circuito se interrumpe.
         │  El documento NO cambió de estado, se reintentará en el siguiente ciclo.
         │  Para S3: idempotente (misma key), seguro reintentar.
         │  Para SOAP: riesgo de duplicado, mitigado por claimDocument (el doc
         │     sigue en PROCESSING, será reclaimado y reintentado).
         │
         ▼  A PARTIR DE AQUÍ, el documento ya está en estado terminal.
         │  Si hay crash, NO se re-procesa. La trazabilidad es completa.
         │
  Paso 6: postProcess(pending, result, traceId)   ← Diferible. No bloqueante.
    └── statusAggregator.updateProductStatus(productId, traceId)
         │
         │  Si este paso falla:
         │  - El documento ya está en SUCCESS/FAILURE → no se re-procesa.
         │  - El producto puede quedar desactualizado → se resuelve con un
         │    scheduled job que recalcula ProductStatus periódicamente.
         │  - O alternativamente: onErrorResume con log.error + métrica.
         │
         └── Retorna FileUploadResult con correlationId intacto.
```

**El `ProcessingCheckpoint` como Value Object inmuable:**

```java
/**
 * Representa un punto de checkpoint en el flujo de procesamiento.
 * Inmutable. Contiene el estado exacto tras cada paso crítico.
 *
 * @param documentId  identificador del documento
 * @param status      estado terminal (SUCCESS, FAILURE, SKIPPED, NOT_SENT)
 * @param correlationId  identificador del recurso externo (eTag S3, ID SOAP)
 * @param errorCode   código de error si aplica (null en éxito)
 * @param timestamp   momento exacto del checkpoint (UTC)
 */
public record ProcessingCheckpoint(
    String documentId,
    String status,
    String correlationId,
    String errorCode,
    Instant timestamp
) {
    public static ProcessingCheckpoint from(ProductDocumentToProcess pending,
                                             FileUploadResult result) {
        return new ProcessingCheckpoint(
            pending.getDocumentId(),
            result.getStatus(),
            result.getCorrelationId(),
            result.getErrorCode(),
            Instant.now()
        );
    }
}
```

**Escenario de recuperación tras crash:**

```
Situación: el pod murió durante el procesamiento.

Al reiniciar, executePendingDocuments() consulta findPendingDocuments().
Este método retorna documentos en estado PENDING, PROCESSING, y RETRY.

  Documento A: llegó a checkpoint → SUCCESS en BD
    → findPendingDocuments() NO lo retorna → no se re-procesa ✓

  Documento B: gateway devolvió OK pero checkpoint FALLÓ → sigue PROCESSING
    → findPendingDocuments() SÍ lo retorna
    → claimDocument() lo bloquea (status → PROCESSING con nuevo traceId)
    → se re-envía (riesgo de duplicado en SOAP, mitigado por idempotency-key si el
       gateway destino la soporta; en S3 no hay problema porque la key es nueva)

  Documento C: ni siquiera llegó al gateway → sigue PENDING
    → se procesa normalmente ✓
```

### 1.2 Clases que serán modificadas

| Clase | Tipo de cambio |
|-------|----------------|
| `DocumentProcessingPipeline` | Se convierte en la **clase abstracta base** del template method |
| `DocumentProcessingOrchestrator` | Se **elimina** — su lógica se fusiona en la clase abstracta |
| `DocumentSenderImpl` | Se **elimina** — el envío se integra en la clase abstracta como paso compartido |
| `DocumentSender` (interface) | Se **elimina** — reemplazada por método concreto en la clase base |
| `DocumentValidationRules` | Se refactoriza para soportar **regex en exclusión de carpetas** |
| `DocumentSkipHandler` | Se **elimina** — la lógica de skip se integra como paso del template method |
| `DomainConfig.java` | Se simplifica drásticamente (de ~130 líneas a ~60 líneas) |

### 1.3 Clases nuevas a crear

| Clase | Responsabilidad |
|-------|----------------|
| `AbstractDocumentProcessingUseCase` | Clase abstracta base con el **template method**. Centraliza: orquestación, envío con resiliencia, registro de `CommunicationLog`, checkpointing y post-procesamiento. |
| `SoapDocumentProcessingUseCase` | Extiende la base. Implementa `validateDocument()` con reglas SOAP específicas. |
| `S3DocumentProcessingUseCase` | Extiende la base. Implementa `validateDocument()` con reglas S3 específicas (peso, exclusión regex). |
| `FolderExclusionRegexConfig` | Value Object para patrones regex de exclusión de carpetas (inyectado vía properties). |
| `ProcessingCheckpoint` | Value Object que representa el punto de checkpoint (estado del documento + timestamp). |

---

## 2. Diseño del Template Method

### 2.1 Diagrama de clases

```
                            ┌──────────────────────────────────────────────┐
                            │    AbstractDocumentProcessingUseCase         │
                            │    (abstract class, domain layer)             │
                            ├──────────────────────────────────────────────┤
                            │  # documentRepository: ProductDocumentRepo   │
                            │  # statusAggregator: ProductStatusAggregator │
                            │  # resilienceOperator: ResilienceOperator    │
                            │  # fileGateway: FileGateway                  │
                            │  # logRepository: CommunicationLogRepository │
                            │  # fileValidator: FileValidator               │
                            ├──────────────────────────────────────────────┤
                            │  + executePendingDocuments(): Flux<Result>   │  ← TEMPLATE METHOD (final)
                            │  # processDocument(doc, traceId): Mono<T>    │  ← TEMPLATE METHOD (final)
                            │  --------------------------------------------│
                            │  # preValidate(doc, traceId): Mono<T>        │  ← HOOK (default: skip rules)
                            │  + validateDocument(doc, traceId): Mono<T>   │  ← ABSTRACT (obligatorio)
                            │  + buildRequest(doc, traceId): Mono<Req>     │  ← ABSTRACT (obligatorio)
                            │  # sendWithResilience(req): Mono<Result>     │  ← CONCRETO (compartido)
                            │  # checkpoint(doc, status): Mono<Void>       │  ← CONCRETO (compartido)
                            │  # postProcess(doc, result, tid): Mono<T>    │  ← CONCRETO (compartido)
                            │  # saveCommunicationLog(doc, result): Mono   │  ← CONCRETO (compartido)
                            └──────────────────────────────────────────────┘
                                    ↑                          ↑
                                    │                          │
                ┌───────────────────┴─────┐    ┌───────────────┴──────────────┐
                │ SoapDocumentProcessing │    │ S3DocumentProcessing          │
                │ UseCase                │    │ UseCase                       │
                ├────────────────────────┤    ├───────────────────────────────┤
                │ - soapProperties       │    │ - s3Properties                │
                │ - folderExclusionRegex │    │ - folderExclusionRegex        │
                │                        │    │                               │
                │ + validateDocument()   │    │ + validateDocument()          │
                │   -> SOAP format       │    │   -> S3 weight, folder regex  │
                │ + buildRequest()       │    │ + buildRequest()              │
                │   -> SOAP metadata     │    │   -> S3 prefix, folders       │
                └────────────────────────┘    └───────────────────────────────┘
```

### 2.2 Template Method: flujo paso a paso

```
executePendingDocuments()                                      ← método final (orquestación)
  │
  ├─ findPendingDocuments()
  ├─ claimDocument(docId)
  │
  └─> processDocument(pending, traceId)                        ← método final (template)
        │
        ├── 1. preValidate(pending, traceId)                   ← HOOK: reglas de negocio (folder skip, origin, size)
        │       │
        │       ├── shouldSkipFolder?(regex) ──> skip → return Mono.just(skippedResult)
        │       ├── shouldSendByOrigin? ────────> not send → return Mono.just(notSentResult)
        │       └── shouldNotSendBySize? ────────> not send → return Mono.just(notSentResult)
        │
        ├── 2. validateDocument(pending, traceId)              ← ABSTRACT: implementado por subclase
        │       │
        │       ├── [S3]:  validar peso (maxFileSizeMb configurable)
        │       ├── [S3]:  excluir carpetas por regex (folderExclusionPatterns)
        │       ├── [SOAP]: validar formato SOAP específico
        │       └── [AMBOS]: delegar a fileValidator.validate()
        │
        ├── 3. buildRequest(validatedDoc, traceId)             ← ABSTRACT: construir DocumentSendRequest
        │       │
        │       └── [AMBOS]: extraer folder info para routing
        │
        ├── 4. sendWithResilience(request)                     ← CONCRETO compartido
        │       │
        │       ├── resilienceOperator.decorate(gateway.send(request), traceId)
        │       ├── onSuccess: saveCommunicationLog(SUCCESS)
        │       └── onError: saveCommunicationLog(FAILURE, errorCode, retryCount)
        │
        ├── 5. checkpoint(pending, result.getStatus())         ← CONCRETO compartido
        │       │
        │       └── Guardar en BD: status + correlationId + errorCode + timestamp
        │
        ├── 6. postProcess(pending, result, traceId)           ← CONCRETO compartido
        │       │
        │       └── statusAggregator.updateProductStatus(productId, traceId)
        │
        └── 7. Retornar FileUploadResult con correlationId
```

### 2.3 Contrato de la clase abstracta

```java
public abstract class AbstractDocumentProcessingUseCase {

    // ============ Dependencias compartidas (inyectadas por constructor) ============

    protected final ProductDocumentRepository documentRepository;
    protected final ProductStatusAggregator statusAggregator;
    protected final ResilienceOperator resilienceOperator;
    protected final FileGateway fileGateway;
    protected final CommunicationLogRepository logRepository;
    protected final FileValidator fileValidator;

    // ============ TEMPLATE METHOD (final = no se puede sobrescribir) ============

    /**
     * Punto de entrada principal. Orquesta el procesamiento de todos los documentos pendientes.
     * NO se sobrescribe. Las subclases especializan vía validateDocument() y buildRequest().
     */
    public final Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument, maxConcurrency())
            .doOnNext(r -> log.info("Processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()));
    }

    /**
     * Template method para procesar UN documento.
     * Define el esqueleto del algoritmo. Las variaciones están en los pasos abstractos.
     */
    protected final Mono<FileUploadResult> processDocument(
            ProductDocumentToProcess pending, String traceId) {

        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed ->
                // Paso 1: pre-validación (hook con default)
                preValidate(pending, traceId)
                    .switchIfEmpty(Mono.defer(() ->
                        // Paso 2: validación específica (abstract)
                        validateDocument(pending, traceId)
                            // Paso 3: construir request (abstract)
                            .flatMap(doc -> buildRequest(doc, traceId))
                            // Paso 4: enviar con resiliencia (concreto)
                            .flatMap(this::sendWithResilience)
                            // Paso 5: checkpoint (concreto)
                            .flatMap(result -> checkpoint(pending, result, traceId))
                            // Paso 6: pos-procesamiento (concreto)
                            .flatMap(result -> postProcess(pending, result, traceId)))
                    ))
            .switchIfEmpty(Mono.fromRunnable(() ->
                log.debug("Document {} claimed by another instance", pending.getDocumentId())));
    }

    // ============ PASOS ABSTRACTOS (obligatorios para cada subclase) ============

    /**
     * Validación específica del tipo de procesamiento.
     * S3: validación de peso y exclusión de carpetas por regex.
     * SOAP: validación de formato y estructura SOAP.
     *
     * @param pending documento pendiente de procesar
     * @param traceId identificador de trazabilidad
     * @return Mono con el documento validado o error (FileValidationException)
     */
    protected abstract Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId);

    /**
     * Construye el DocumentSendRequest específico para el gateway.
     *
     * @param validDoc documento ya validado
     * @param traceId identificador de trazabilidad
     * @return Mono con el request listo para enviar
     */
    protected abstract Mono<DocumentSendRequest> buildRequest(
            ProductDocumentToProcess validDoc, String traceId);

    // ============ HOOK (opcional, con implementación default) ============

    /**
     * Pre-validación con reglas de negocio compartidas (skip folders, origin, size).
     * Las subclases PUEDEN sobrescribir para añadir pre-validaciones adicionales,
     * pero DEBEN llamar a super.preValidate() primero.
     *
     * Si el documento debe ser saltado, retorna Mono.just(un FileUploadResult con status=skip).
     * Ese Mono NO vacío hará que el template method short-circuitee (no ejecuta pasos 2-6).
     */
    protected Mono<FileUploadResult> preValidate(
            ProductDocumentToProcess pending, String traceId) {
        // lógica de skip compartida (ver sección 2.4)
        // Devuelve Mono.empty() si el documento NO debe saltarse (continúa),
        // o Mono.just(result) si SÍ debe saltarse (short-circuit).
    }

    // ============ PASOS CONCRETOS COMPARTIDOS ============

    protected Mono<FileUploadResult> sendWithResilience(DocumentSendRequest request);
    protected Mono<FileUploadResult> checkpoint(
            ProductDocumentToProcess pending, FileUploadResult result, String traceId);
    protected Mono<FileUploadResult> postProcess(
            ProductDocumentToProcess pending, FileUploadResult result, String traceId);
    protected Mono<Void> saveCommunicationLog(
            ProductDocumentToProcess pending, FileUploadResult result,
            String traceId, int retryCount);
    protected int maxConcurrency();
    protected abstract String implementationName();
}
```

---

## 3. Especialización: Validación por Regex para Exclusión de Carpetas

### 3.1 Propósito

Reemplazar `String.contains()` en `DocumentValidationRules.shouldSkipFolder()` con patrones regex configurables desde `application.yml`. Esto permite exclusiones como:

- `.*_backup$` — carpetas que terminan en `_backup`
- `temp/.*` — cualquier ruta bajo `temp/`
- `archive/202[0-2]/.*` — solo años 2020–2022 en archive
- `.*/\.trash/.*` — carpetas ocultas `.trash`

### 3.2 Nueva configuración en application.yml

```yaml
app:
  processors:
    soap:
      max-size: 10485760
      allowed-types: pdf,txt,csv
      max-file-size-mb: 50
      folders-to-skip:              # lista legacy (simple contains, sin cambios)
        - "/tmp/uploads/trash"
      folder-exclusion-regex:       # NUEVA: patrones regex para exclusión
        - ".*/\\.trash/.*"
        - "temp/.*"
    s3:
      max-size: 52428800
      allowed-types: pdf,txt,csv,zip
      max-file-size-mb: 100
      folder-exclusion-regex:
        - ".*_backup$"
        - "archive/202[0-2]/.*"
        - ".*/\\.DS_Store$"
```

### 3.3 Implementación

```java
/**
 * Value object que encapsula los patrones regex de exclusión de carpetas.
 * Se construye desde ProcessorSettings y se inyecta en las subclases concretas.
 */
public class FolderExclusionRegexConfig {

    private final List<Pattern> exclusionPatterns;

    public FolderExclusionRegexConfig(List<String> regexPatterns) {
        this.exclusionPatterns = regexPatterns.stream()
            .map(Pattern::compile)
            .toList();
    }

    /**
     * Evalúa si la ruta de origen debe excluirse según los patrones regex.
     * @param origin ruta completa de origen (ej: "/data/backup/file.pdf")
     * @return true si coincide con al menos un patrón de exclusión
     */
    public boolean shouldExclude(String origin) {
        if (origin == null || exclusionPatterns.isEmpty()) {
            return false;
        }
        return exclusionPatterns.stream()
            .anyMatch(pattern -> pattern.matcher(origin).matches());
    }

    public boolean isEmpty() {
        return exclusionPatterns.isEmpty();
    }
}
```

### 3.4 Integración en `AbstractDocumentProcessingUseCase.preValidate()`

```java
protected Mono<FileUploadResult> preValidate(
        ProductDocumentToProcess pending, String traceId) {

    // 1. Legacy: skip por carpeta (String.contains)
    if (validationRules.shouldSkipFolder(pending.getOrigin())) {
        return skipDocument(pending, traceId, SKIPPED, "Folder excluded: " + pending.getOrigin());
    }

    // 2. NUEVA: exclusión por regex
    if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
        return skipDocument(pending, traceId, SKIPPED,
            "Folder excluded by regex: " + pending.getOrigin());
    }

    // 3. Origin patterns
    if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
        return skipDocument(pending, traceId, NOT_SENT, "Origin not matched: " + pending.getOrigin());
    }

    // 4. Size limit
    long fileSize = pending.getContent() != null ? pending.getContent().length : 0;
    if (validationRules.shouldNotSendBySize(fileSize)) {
        return skipDocument(pending, traceId, NOT_SENT, "Size exceeded: " + fileSize);
    }

    // Documento OK para continuar
    return Mono.empty();
}
```

---

## 4. Resiliencia: Checkpointing y Reintentos

### 4.1 Principio

Cada paso del template method que modifica estado debe registrar un **checkpoint** inmutable. Si el proceso falla, el checkpoint permite:

1. **Retomar desde el último paso exitoso** (no desde cero).
2. **Auditar la trazabilidad completa** (qué pasó y cuándo).
3. **Evitar doble envío** (si el gateway ya procesó el documento pero falló al guardar el status).

### 4.2 Estrategia de checkpointing

```
Estado del documento  →  Acción realizada  →  Checkpoint guardado
─────────────────────────────────────────────────────────────────
PENDING               →  claimDocument()   →  PROCESSING + traceId
PROCESSING            →  gateway.send() OK →  SUCCESS + correlationId + CommunicationLog
PROCESSING            →  gateway.send() FAIL → RETRY + errorCode + CommunicationLog(FAIL)
RETRY                 →  gateway.send() OK →  SUCCESS + correlationId + CommunicationLog
RETRY (retries>max)   →  gateway.send() FAIL → FAILURE + errorCode + CommunicationLog(FAIL)
SUCCESS / FAILURE     →  statusAggregator  →  ProductStatus actualizado (sin cambio en doc)
```

### 4.3 Reintentos con backoff uniforme (Gateway-level)

Ambos gateways (`S3GatewayAdapter` y `SoapGatewayAdapter`) deben compartir la misma política de reintentos, definida en el `ResilienceOperator`:

```
Configuración (CircuitBreakerProperties):
  retry.maxAttempts:    3
  retry.backoffMs:     500   (backoff exponencial: 500ms → 1s → 2s antes del timeout)
  retry.retryableStatuses: TIMEOUT, CONNECTION_REFUSED, SERVICE_UNAVAILABLE(503), TOO_MANY_REQUESTS(429)
```

### 4.4 `CommunicationLog` en todos los outcomes

**Antes** (rama `v2.0`): solo se guardaba en éxito (`saveSuccessLog`).

**Después** (rama `v3.0`): se guarda en **cada** intento, con `retryCount` y `errorCode`:

```java
protected Mono<Void> saveCommunicationLog(
        ProductDocumentToProcess pending, FileUploadResult result,
        String traceId, int retryCount) {

    CommunicationLog logEntry = CommunicationLog.builder()
        .traceId(traceId)
        .documentId(pending.getDocumentId())
        .status(result.getStatus())
        .retryCount(retryCount)          // ← antes hardcoded a DEFAULT_RETRY_COUNT
        .errorCode(result.getErrorCode()) // ← antes nunca se populaba
        .filename(pending.getFilename())
        .createdAt(Instant.now())
        .build();

    return logRepository.save(logEntry).then();
}
```

---

## 5. Plan de Acción Detallado

### Fase 1: Crear la clase abstracta `AbstractDocumentProcessingUseCase`

**Archivo:** `src/main/java/.../domain/usecase/AbstractDocumentProcessingUseCase.java`

| # | Acción | Detalle |
|---|--------|---------|
| 1.1 | Crear la clase abstracta | Extraer de `DocumentProcessingPipeline` toda la lógica compartida: `processDocument()`, `sendWithResilience()`, `checkpoint()`, `postProcess()`. |
| 1.2 | Definir `processDocument()` como `final` | El esqueleto del algoritmo no debe ser sobrescrito. |
| 1.3 | Definir `executePendingDocuments()` como `final` | La orquestación de encontrar + claim + procesar es idéntica para SOAP y S3. |
| 1.4 | Declarar métodos abstractos | `validateDocument()`, `buildRequest()`, `implementationName()`. |
| 1.5 | Declarar método hook | `preValidate()` con implementación default (usa `DocumentValidationRules` + `FolderExclusionRegexConfig`). |
| 1.6 | Incluir `saveCommunicationLog()` con soporte para fallos | Guardar log en éxito y en fallo, con `retryCount` y `errorCode`. |

### Fase 2: Crear las subclases concretas

| # | Acción | Archivo |
|---|--------|---------|
| 2.1 | `SoapDocumentProcessingUseCase` | `domain/usecase/SoapDocumentProcessingUseCase.java` |
| 2.2 | `S3DocumentProcessingUseCase` | `domain/usecase/S3DocumentProcessingUseCase.java` |

**`S3DocumentProcessingUseCase.validateDocument()`** implementa:
- Validación de peso: `pending.getContent().length > s3MaxFileSizeBytes`
- Exclusión por regex: `folderExclusionRegex.shouldExclude(pending.getOrigin())`
- Delegación a `fileValidator.validate()` para validación común (extensión, filename)

**`SoapDocumentProcessingUseCase.validateDocument()`** implementa:
- Validación de formato SOAP (por ejemplo, rechazar binarios que no sean `text/xml` compatibles)
- Delegación a `fileValidator.validate()`

### Fase 3: Crear Value Objects de soporte

| # | Acción | Archivo |
|---|--------|---------|
| 3.1 | `FolderExclusionRegexConfig` | `domain/valueobject/FolderExclusionRegexConfig.java` |
| 3.2 | `ProcessingCheckpoint` | `domain/valueobject/ProcessingCheckpoint.java` |

### Fase 4: Refactorizar `DomainConfig.java`

**Objetivo:** Simplificar la configuración de ~130 líneas a ~60 líneas.

**Antes:** 2 beans de `FileValidator`, 2 de `DocumentValidationRules`, 2 de `DocumentProcessingPipeline`, 2 de `DocumentProcessingOrchestrator`, 1 de `DocumentSender`, 1 de `DocumentSenderImpl`.

**Después:** 1 bean de `AbstractDocumentProcessingUseCase` (SOAP, default) + 1 bean de `S3DocumentProcessingUseCase` (`@Profile("s3")`).

```java
@Configuration
public class DomainConfig {

    // --- Componentes compartidos ---

    @Bean
    public ProductStatusAggregator productStatusAggregator(...) { ... }

    @Bean
    public FolderExclusionRegexConfig soapFolderExclusion(ProcessorConfig c) {
        return new FolderExclusionRegexConfig(c.getSoap().getFolderExclusionRegex());
    }

    @Bean
    @Profile("s3")
    public FolderExclusionRegexConfig s3FolderExclusion(ProcessorConfig c) {
        return new FolderExclusionRegexConfig(c.getS3().getFolderExclusionRegex());
    }

    // --- Use Cases ---

    @Bean
    public SoapDocumentProcessingUseCase soapUseCase(
            ProductDocumentRepository dr, ProductStatusAggregator sa,
            ResilienceOperator ro, FileGateway fg, CommunicationLogRepository lr,
            ProcessorConfig pc, FolderExclusionRegexConfig fe) {
        return new SoapDocumentProcessingUseCase(dr, sa, ro, fg, lr,
            new FileValidator(pc.getSoap()), new DocumentValidationRules(pc.getSoap()), fe);
    }

    @Bean
    @Profile("s3")
    public S3DocumentProcessingUseCase s3UseCase(
            /* dependencias análogas con S3 */) {
        return new S3DocumentProcessingUseCase(..., pc.getS3());
    }
}
```

### Fase 5: Actualizar `ProductHandler`

| # | Acción | Detalle |
|---|--------|---------|
| 5.1 | Cambiar dependencias | De `DocumentProcessingOrchestrator soapDocumentUseCase + Optional<DocumentProcessingOrchestrator> s3DocumentUseCase` a `AbstractDocumentProcessingUseCase soapUseCase + Optional<AbstractDocumentProcessingUseCase> s3UseCase`. |
| 5.2 | Actualizar `resolveUseCase()` | Misma lógica, tipos actualizados. |
| 5.3 | Actualizar `processPendingProducts()` | `useCase.executePendingDocuments()` ya está en la clase abstracta. |

### Fase 6: Eliminar clases redundantes

| # | Archivo a eliminar | Razón |
|---|-------------------|-------|
| 6.1 | `DocumentProcessingPipeline.java` | Fusionada en `AbstractDocumentProcessingUseCase` |
| 6.2 | `DocumentProcessingOrchestrator.java` | Fusionada en `AbstractDocumentProcessingUseCase` |
| 6.3 | `DocumentSenderImpl.java` | Lógica integrada en `sendWithResilience()` |
| 6.4 | `DocumentSender.java` | Reemplazada por método concreto `sendWithResilience()` |
| 6.5 | `DocumentSkipHandler.java` | Fusionada en `preValidate()` y `skipDocument()` de la clase base |

### Fase 7: Unificar reintentos en `S3GatewayAdapter`

| # | Acción | Detalle |
|---|--------|---------|
| 7.1 | Añadir `retryWhen` al S3 Gateway | Paridad con SOAP: `Retry.backoff(properties.retryAttempts(), ...)`. |
| 7.2 | Misma política de reintentos | Configurable desde `S3Properties` (nuevos campos: `retryAttempts`, `retryBackoffMillis`). |
| 7.3 | Registrar `CommunicationLog` en fallos S3 | `onErrorResume` actualizado con `saveCommunicationLog(FALLÓ)`. |

### Fase 8: Actualizar configuración de properties

**`S3Properties.java`** — añadir campos de resiliencia:

```java
@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
    @NotBlank String bucketName,
    @NotBlank String region,
    String endpoint,
    boolean pathStyleAccess,
    String accessKey,
    String secretKey,
    @Min(0) int retryAttempts,         // NUEVO
    @Min(100) int retryBackoffMillis   // NUEVO
) {}
```

**`ProcessorSettings.java`** — añadir lista de regex:

```java
// NUEVO campo
private List<String> folderExclusionRegex = List.of();

public List<String> getFolderExclusionRegex() { return folderExclusionRegex; }
public void setFolderExclusionRegex(List<String> patterns) {
    this.folderExclusionRegex = patterns != null ? patterns : List.of();
}
```

### Fase 9: Actualizar tests

| # | Acción |
|---|--------|
| 9.1 | `SoapDocumentProcessingUseCaseTest` — test de integración mockeando `FileGateway` |
| 9.2 | `S3DocumentProcessingUseCaseTest` — test de validación de peso y exclusión regex |
| 9.3 | `FolderExclusionRegexConfigTest` — tests unitarios para patrones regex |
| 9.4 | `DocumentValidationRulesTest` — actualizar para cubrir nuevo flujo con regex |
| 9.5 | Eliminar tests de clases eliminadas: `DocumentProcessingPipelineTest`, etc. |
| 9.6 | `ProductHandlerTest` — actualizar imports a los nuevos tipos |

---

## 6. Resumen de cambios

```
ARCHIVOS MODIFICADOS (10):
  src/main/java/.../application/config/DomainConfig.java
  src/main/java/.../domain/usecase/DocumentValidationRules.java
  src/main/java/.../infrastructure/helpers/config/ProcessorSettings.java
  src/main/java/.../infrastructure/drivenadapters/aws/config/S3Properties.java
  src/main/java/.../infrastructure/drivenadapters/aws/S3GatewayAdapter.java
  src/main/java/.../infrastructure/drivenadapters/soap/SoapGatewayAdapter.java
  src/main/java/.../infrastructure/entrypoints/rest/handler/ProductHandler.java
  src/test/java/.../domain/usecase/DocumentValidationRulesTest.java
  src/test/java/.../infrastructure/entrypoints/rest/handler/ProductHandlerTest.java
  src/main/resources/application.yml

ARCHIVOS NUEVOS (5):
  src/main/java/.../domain/usecase/AbstractDocumentProcessingUseCase.java
  src/main/java/.../domain/usecase/SoapDocumentProcessingUseCase.java
  src/main/java/.../domain/usecase/S3DocumentProcessingUseCase.java
  src/main/java/.../domain/valueobject/FolderExclusionRegexConfig.java
  src/main/java/.../domain/valueobject/ProcessingCheckpoint.java

ARCHIVOS ELIMINADOS (6):
  src/main/java/.../domain/usecase/DocumentProcessingPipeline.java
  src/main/java/.../domain/usecase/DocumentProcessingOrchestrator.java
  src/main/java/.../domain/usecase/DocumentSender.java
  src/main/java/.../domain/usecase/DocumentSenderImpl.java
  src/main/java/.../domain/usecase/DocumentSkipHandler.java
  (y sus tests correspondientes)

MÉTRICAS:
  - 131 líneas en DomainConfig.java → ~60 líneas
  - 6 artefactos eliminados (clases redundantes fusionadas)
  - 2 gateways con resiliencia uniforme
  - Trazabilidad 100%: CommunicationLog en éxito Y en fallo
```

---

## 7. Riesgos y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|---------|------------|
| El refactor rompe los tests existentes | Media | Alto | Ejecutar `./gradlew test` tras cada fase; actualizar tests incrementalmente. |
| `CommunicationLog` extra en cada fallo puede sobrecargar BD | Baja | Medio | Configurar `retryAttempts` máximo (3). La limpieza de logs se realiza manualmente vía revisión de BD cuando sea necesario. |
| La firma del template method (`final`) limita extensibilidad futura | Baja | Bajo | El patrón Template Method es extensible por diseño: nuevas subclases = nuevos gateways (FTP, etc.). |
| El cambio de `contains` a regex incrementa latencia de validación | Muy baja | Bajo | Los patrones se compilan una vez (en constructor) y `Pattern.matcher().matches()` es O(n+m). Para 10 patrones sobre rutas de ~100 chars, el overhead es despreciable. |
| Conflicto de merge con `feature/v2.0` | Media | Medio | La rama `v2.0` ya consolidó tipos (P1-P8). La `v3.0` parte desde `v2.0` con todos esos cambios integrados. |

---

## 8. Orden de ejecución recomendado

```
Fase 1 ──> Fase 2 ──> Fase 3 ──> Fase 4 ──> Fase 5 ──> Fase 6 ──> Fase 7 ──> Fase 8 ──> Fase 9
                                                                                              │
                                                                                        ┌─────┴──────┐
                                                                                        │  Verificar  │
                                                                                        │  tests +    │
                                                                                        │  build OK   │
                                                                                        └────────────┘
```

Cada fase es autocontenida y puede ser validada (`./gradlew compileJava`) antes de avanzar a la siguiente.

---

## 9. Verificación final

```bash
./gradlew clean compileJava compileTestJava test jacocoTestReport
```

**Criterios de aceptación:**
- [ ] `compileJava` sin errores ni warnings
- [ ] `compileTestJava` sin errores
- [ ] Todos los tests existentes pasan (sin regresiones)
- [ ] `DocumentProcessingPipeline` y `DocumentProcessingOrchestrator` ya no existen en `src/main/java`
- [ ] `AbstractDocumentProcessingUseCase` contiene el template method `processDocument()` marcado `final`
- [ ] `S3DocumentProcessingUseCase.validateDocument()` incluye validación de peso y exclusión regex
- [ ] `CommunicationLog` se guarda tanto en éxito como en fallo
- [ ] `S3GatewayAdapter` tiene política de reintentos equivalente a `SoapGatewayAdapter`
- [ ] `application.yml` incluye `folder-exclusion-regex` para ambos procesadores
