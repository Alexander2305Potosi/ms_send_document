# Plan Conjunto: Simplificación del Pipeline de Procesamiento

> Rama: `feature/v2.0` | Fecha: 2026-04-27
>
> Revisores: Junior Backend (2 años exp.) · Experto Backend (15 años exp.)
>
> Consenso: El pipeline tiene **demasiadas transformaciones de objetos** y **abstracciones desproporcionadas** para la complejidad real del dominio ("validar → enviar → actualizar estado").

---

## Diagnóstico Conjunto

Ambos revisores, de forma independiente, identificaron los mismos problemas estructurales:

| Problema | Gravedad | Junior | Experto |
|----------|----------|--------|---------|
| 6 tipos de objeto para 1 documento, 4 transformaciones son field-copy puro | **Alta** | "9 formas de objeto distintas" | "4 de 5 transformaciones son redundantes" |
| `DocumentResult` y `FileUploadResult` son idénticos campo a campo | **Alta** | "Diferencias no obvias desde nombres" | "Copia builder-a-builder de 7 campos, cero lógica" |
| `FileData` es un DTO intermedio innecesario | **Alta** | " Solo para validar, copia 6 campos" | "`FileValidator` podría aceptar `ProductDocumentToProcess`" |
| `SoapRequest` se usa para SOAP y S3 — el nombre miente | **Media** | "El nombre miente" | "`parentFolder`/`childFolder` sin sentido en S3" |
| `SoapCommunicationLog` guarda logs de S3 también | **Media** | Detectado | Detectado |
| Circuit Breaker anulado por `onErrorResume` en los Senders | **Crítica** | No detectado | "El CB nunca registrará un fallo" |
| ~400 líneas de código muerto (`AbstractProcessDocumentsUseCase`, etc.) | **Media** | "Horas perdidas entendiendo código zombie" | "Riesgo de instanciar el path viejo" |
| `DocumentProcessingOrchestrator` es un wrapper de 60 líneas para 1 llamada | **Baja** | No detectado | "3 líneas de lógica real" |
| 8 interfaces con 1 sola implementación (89%) | **Media** | No detectado | "Indirección sin abstracción" |
| 8 archivos de constantes, algunos con duplicados | **Baja** | Detectado | Detectado |

---

## Cambio P1 — Fusionar `DocumentResult` + `FileUploadResult` en una sola clase

**Acuerdo:** Ambos revisores. El experto demostró que los 7 campos son idénticos.

### Situación actual

```java
// DocumentResult.java — 7 campos (status, message, correlationId, traceId,
//                           processedAt, externalReference, success)
// FileUploadResult.java — 7 campos (los mismos, mismo orden)
//
// DocumentProcessingPipeline.java:151-161 — copia campo a campo:
private FileUploadResult toFileUploadResult(DocumentResult result, String traceId) {
    return FileUploadResult.builder()
        .status(result.getStatus())
        .message(result.getMessage())
        .correlationId(result.getCorrelationId())
        .traceId(traceId)
        .processedAt(result.getProcessedAt())
        .externalReference(result.getExternalReference())
        .success(result.isSuccess())
        .build();
}
```

### Solución

Eliminar `DocumentResult`. Usar `FileUploadResult` como única clase de resultado en todo el pipeline:

- `DocumentSender.send()` devuelve `Mono<FileUploadResult>` en vez de `Mono<DocumentResult>`
- `SoapDocumentSender` y `S3DocumentSender` construyen `FileUploadResult` directamente
- Se elimina `toFileUploadResult()` del pipeline

### Beneficio

- 1 clase eliminada, 1 método eliminado, 1 transformación eliminada
- Sin ambigüedad sobre "¿cuál resultado uso?"

---

## Cambio P2 — Eliminar `FileData`: `FileValidator` acepta `ProductDocumentToProcess`

**Acuerdo:** El experto inició este punto. El junior confirmó que `FileData` causa confusión.

### Situación actual

```java
// DocumentProcessingPipeline.java:98-108 — crea FileData copiando campos:
private Mono<FileData> validateFile(ProductDocumentToProcess pending) {
    FileData fileData = FileData.builder()
        .documentId(pending.getDocumentId())
        .content(pending.getContent())
        .filename(pending.getFilename())
        .size(pending.getContent() != null ? pending.getContent().length : 0)
        .contentType(pending.getContentType())
        .traceId("")    // <-- traceId vacío, no se usa en validación
        .build();
    return fileValidator.validate(fileData);
}
```

`FileData` tiene 6 campos. 5 vienen de `ProductDocumentToProcess`. El sexto (`traceId`) se pasa vacío.

### Solución

`FileValidator` acepta `ProductDocumentToProcess` (o una interfaz `HasContent` con `getContent()`, `getFilename()`, `getContentType()`, `getSize()`):

```java
// FileValidator.java — nuevo método:
public Mono<ProductDocumentToProcess> validate(ProductDocumentToProcess pending) {
    return Mono.just(pending)
        .flatMap(this::validateSize)
        .flatMap(this::validateExtension)
        .flatMap(this::validateFilename);
}

private Mono<ProductDocumentToProcess> validateSize(ProductDocumentToProcess p) {
    long size = p.getContent() != null ? p.getContent().length : 0;
    if (size > config.maxSize()) {
        return Mono.error(...);
    }
    return Mono.just(p);
}
```

### Beneficio

- 1 clase eliminada (`FileData`)
- 1 transformación eliminada
- `validateFile()` pasa de ~10 líneas a 1 llamada

---

## Cambio P3 — Renombrar `SoapRequest` → `DocumentSendRequest`

**Acuerdo:** Ambos revisores. El junior: "el nombre miente". El experto: "confusión semántica".

### Situación actual

`SoapRequest` se usa como parámetro en:
- `DocumentSender.send(SoapRequest)` — interfaz genérica
- `S3Gateway.upload(SoapRequest)` — ¡para S3!
- `ExternalSoapGateway.sendFile(SoapRequest)` — único uso legítimo "SOAP"

Los campos (`fileContent`, `filename`, `contentType`, `fileSize`, `traceId`, `parentFolder`, `childFolder`) son genéricos de transporte de archivo. Nada es SOAP.

### Solución

Renombrar `SoapRequest` → `DocumentSendRequest`. Los imports se actualizan en toda la base.

### Beneficio

- `S3Gateway.upload(DocumentSendRequest)` — el nombre no miente
- Nuevos mecanismos de envío (FTP, REST) no heredan confusión SOAP

---

## Cambio P4 — Renombrar `SoapCommunicationLog` → `CommunicationLog`

**Acuerdo:** Ambos revisores detectaron que almacena logs de S3 también.

### Situación actual

- `SoapDocumentSender.saveSuccessLog()` crea `SoapCommunicationLog`
- `S3DocumentSender.saveSuccessLog()` también crea `SoapCommunicationLog` (línea 65)
- La tabla en BD se llama `soap_communication_log`

Ambos protocolos guardan en la misma entidad con nombre "SOAP".

### Solución

Renombrar clase a `CommunicationLog`, repositorio a `CommunicationLogRepository`, tabla a `communication_log`.

---

## Cambio P5 — Eliminar código muerto (3 clases, ~400 líneas)

**Acuerdo:** Ambos revisores. El junior perdió tiempo leyéndolas; el experto advierte riesgo.

### Clases a eliminar

| Archivo | Líneas | Motivo |
|---------|--------|--------|
| `AbstractProcessDocumentsUseCase.java` | ~270 | Arquitectura vieja (herencia). Reemplazada por Pipeline + Orchestrator. |
| `SoapDocumentUseCase.java` | ~56 | Subclase de la arquitectura vieja. Reemplazada por `SoapDocumentSender`. |
| `S3DocumentUseCase.java` | ~78 | Subclase de la arquitectura vieja. Reemplazada por `S3DocumentSender`. |

Ninguna está referenciada en `DomainConfig.java`. git las preserva.

---

## Cambio P6 (CRÍTICO) — Arreglar Circuit Breaker: eliminar `onErrorResume` de los Senders

**Acuerdo:** Detectado por el experto. El junior no lo identificó — lo que confirma que es un bug silencioso.

### Situación actual

```java
// SoapDocumentSender.java:40-51 — atrapa TODO error y devuelve Mono exitoso:
.onErrorResume(error -> {
    return Mono.just(DocumentResult.builder()   // ← Mono.just = señal de éxito
        .status(DocumentStatus.FAILURE_VALUE)    // ← el CB nunca ve este fallo
        .success(false)
        .build());
});
```

El `CircuitBreakerOperator` en `sendWithCircuitBreaker()` mide señales reactivas (`onError` / `onComplete`). Como `onErrorResume` convierte todo error en `onComplete`, el circuit breaker **nunca registra un fallo, nunca se abre**.

### Solución

**Opción A (recomendada por el experto):** Eliminar `onErrorResume`. Los errores del gateway se propagan como `SoapCommunicationException`. El pipeline ya tiene manejo de errores. El circuit breaker ve las señales reales.

**Opción B:** Mover `onErrorResume` **fuera** del `CircuitBreakerOperator` para que el CB vea el error original, y el fallback se aplique después:

```java
// sendWithCircuitBreaker: el CB ve el error real
return Mono.just(request)
    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
    .flatMap(documentSender::send);  // sin onErrorResume aquí

// el fallback se aplica en updateStatuses(), fuera del CB
```

### Beneficio

- El circuit breaker **funciona**: se abre cuando el servicio externo falla repetidamente
- `CircuitBreakerConfiguration.java` y `CircuitBreakerProperties.java` dejan de ser configuración muerta

---

## Cambio P7 — Reducir de 8 interfaces con 1 impl a 2 interfaces con múltiples impls

**Acuerdo:** Iniciado por el experto. El junior notó la fricción de saltar entre puertos y adaptadores.

### Situación actual

| Interfaz | Implementación |
|----------|---------------|
| `ExternalSoapGateway` | `SoapGatewayAdapter` |
| `S3Gateway` | `S3GatewayAdapter` |
| `ProductDocumentRepository` | `R2dbcProductDocumentRepository` |
| `ProductRepository` | `R2dbcProductRepository` |
| `SoapCommunicationLogRepository` | `R2dbcSoapCommunicationLogRepository` |
| `ProductRestGateway` | `ProductRestGatewayAdapter` |
| `AsyncOperationRepository` | `InMemoryAsyncOperationRepository` |
| `FileValidationConfig` | `ProcessorSettings` |
| `DocumentSender` | `SoapDocumentSender`, `S3DocumentSender` ← **única con >1 impl** |

### Solución

**No eliminar** las interfaces (rompería hexagonal), pero **consolidar** algunas:

1. Unificar `ProductDocumentRepository` + `ProductRepository` en un solo puerto (`DocumentStore`), ya que siempre se usan juntos en el pipeline. Esto reduce 2 beans de `DomainConfig`.
2. Unificar `ExternalSoapGateway` + `S3Gateway` bajo una interfaz común `FileGateway` con método `send(DocumentSendRequest)`. `SoapGatewayAdapter` y `S3GatewayAdapter` la implementan. Esto elimina 1 interfaz y hace que `SoapDocumentSender`/`S3DocumentSender` colapsen en un solo `DocumentSenderImpl`.

### Beneficio

- 8 interfaces → 4 interfaces
- `DomainConfig`: ~14 beans → ~8 beans
- `SoapDocumentSender` + `S3DocumentSender` → un solo `DocumentSenderImpl` que recibe `FileGateway`

---

## Cambio P8 — Consolidar archivos de constantes (8 → 3)

**Acuerdo:** El junior notó confusión con tantos archivos de constantes. El experto detectó duplicación (`GATEWAY_TIMEOUT` en 2 lugares).

### Situación actual

| Archivo | Constantes |
|---------|-----------|
| `DocumentErrorCodes.java` | 12 |
| `DocumentProcessingConstants.java` | ~10 |
| `FileValidationErrorCodes.java` | ~6 |
| `FileValidatorConstants.java` | ~5 |
| `ProductDocumentConstants.java` | ~4 |
| `SoapResponseConstants.java` | ~3 |
| `SoapErrorCodes.java` | 4 |
| `RestApiConstants.java` | ~6 |

### Solución

Consolidar en 3 archivos semánticos:

| Archivo | Agrupa |
|---------|--------|
| `ProcessingResultCodes.java` | `DocumentErrorCodes` + `SoapErrorCodes` + `FileValidationErrorCodes` (códigos de error) |
| `ProcessingMessages.java` | `DocumentProcessingConstants` + `FileValidatorConstants` (mensajes y defaults) |
| `ApiConstants.java` | `RestApiConstants` + `SoapResponseConstants` + `ProductDocumentConstants` |

### Beneficio

- 8 archivos → 3 archivos
- Sin duplicación (`GATEWAY_TIMEOUT` en un solo lugar)
- Un nuevo desarrollador busca constantes en 3 lugares, no en 8

---

## Resumen: Pipeline antes vs. después

### Antes (actual)

```
ProductDocumentToProcess
  → FileData              (field copy × 6)
  → SoapRequest           (field copy × 8 + folderInfo)
  → [SOAP call]
  → SoapResponse          (external)
  → DocumentResult        (field copy × 7)
  → FileUploadResult      (field copy × 7 — idéntico)
```
**6 tipos de objeto, 5 transformaciones, 4 field-copies redundantes**

### Después (propuesto)

```
ProductDocumentToProcess
  → [validación directa sin DTO intermedio]
  → DocumentSendRequest   (único request object)
  → [SOAP / S3 call]
  → FileUploadResult      (único result object, construido directo desde la response)
```
**3 tipos de objeto, 2 transformaciones con lógica real**

---

## Orden de implementación

| Orden | Cambio | Riesgo | Depende de |
|-------|--------|--------|------------|
| 1 | P5 - Eliminar código muerto (3 clases) | Bajo | Nada |
| 2 | P1 - Fusionar `DocumentResult` + `FileUploadResult` | Medio | P5 |
| 3 | P2 - Eliminar `FileData` | Medio | P5 |
| 4 | P3 - Renombrar `SoapRequest` → `DocumentSendRequest` | Medio | P2 |
| 5 | P4 - Renombrar `SoapCommunicationLog` → `CommunicationLog` | Medio | Nada |
| 6 | P6 - Arreglar Circuit Breaker (`onErrorResume`) | **Alto** | P1 |
| 7 | P8 - Consolidar archivos de constantes | Bajo | P4 |
| 8 | P7 - Reducir interfaces (8 → 4) | **Alto** | P3, P4, P6 |

### Hitos

- **Hito A** (P1–P5): Eliminar código muerto, fusionar duplicados, renombrar. ~6 clases menos. Riesgo bajo-medio. **1 PR.**
- **Hito B** (P6): Arreglar Circuit Breaker. Crítico. Requiere tests de integración con CB real. **PR separado.**
- **Hito C** (P7–P8): Consolidar puertos y constantes. Cambio arquitectónico. **PR separado.**

---

## Riesgos identificados

- **P6 (Circuit Breaker):** Si el servicio externo está caído y el CB ahora se abre correctamente, el comportamiento en producción cambia: documentos se acumulan como pendientes en vez de marcarse FAILURE con `onErrorResume`. Hay que validar que el negocio acepta este cambio de semántica.
- **P7 (Unificar puertos):** Toca `DomainConfig` y todas las clases que importan los puertos antiguos. Hacer con refactor automático del IDE, no a mano.
- **P3 (Renombrar SoapRequest):** Afecta tests, `SoapMapper`, `SoapEnvelopeWrapper`. El IDE lo maneja, pero hay que revisar cobertura de tests post-rename.
