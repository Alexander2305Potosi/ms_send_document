# Plan: Split `historico_documentos` into `documentos` + `historico_documentos`

## Contexto

La tabla actual `historico_documentos` mezcla dos responsabilidades en una sola tabla:

- **Filas de metadatos** (`caso_uso IS NULL`): representan el estado actual del documento (PENDING, IN_PROGRESS, PROCESSED, FAILED, SYNCED)
- **Filas de trazabilidad** (`caso_uso IS NOT NULL`): registran cada intento de envio a SOAP o S3 (SUCCESS/FAILURE con codigos de error)

Esta refactorizacion separa ambas responsabilidades en dos tablas con una relacion FK numerica, alineandose con Clean Architecture y haciendo explicitas las consultas y el manejo de estado.

---

## Target Schema

### `documentos` — metadata + estado actual

```sql
CREATE TABLE IF NOT EXISTS documentos (
    id BIGSERIAL PRIMARY KEY,
    id_documento VARCHAR(100) NOT NULL,
    id_producto VARCHAR(100) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    clave_documento VARCHAR(255),
    nombre VARCHAR(255),
    propietario VARCHAR(255),
    ruta TEXT,
    estado VARCHAR(100) NOT NULL,          -- PENDING / IN_PROGRESS / PROCESSED / FAILED
    version_contrato VARCHAR(50),
    mensaje_error TEXT,
    es_zip BOOLEAN DEFAULT FALSE,
    nombre_zip_padre VARCHAR(255),
    caso_uso VARCHAR(100),                 -- "SOAP" / "S3"
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documentos_estado ON documentos(estado);
CREATE INDEX idx_documentos_documento_id ON documentos(id_documento);
CREATE INDEX idx_documentos_producto_id ON documentos(id_producto);
CREATE INDEX idx_documentos_caso_uso ON documentos(caso_uso);
```

### `historico_documentos` — trazabilidad / auditoria unicamente

```sql
CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGSERIAL PRIMARY KEY,
    documento_id BIGINT NOT NULL REFERENCES documentos(id),
    nombre_archivo VARCHAR(255),           -- filename del documento procesado (trazabilidad de entries de ZIP)
    operacion VARCHAR(50),                 -- "SYNC", "SOAP", "S3"
    message_id VARCHAR(100),              -- trace ID del request HTTP
    resultado VARCHAR(50),                -- "SUCCESS" / "FAILURE"
    codigo_error VARCHAR(50),             -- GATEWAY_TIMEOUT, BAD_GATEWAY, INVALID_ZIP, BUSINESS_RULE_SKIP
    mensaje_error TEXT,
    stack_trace TEXT,
    reintentos INTEGER NOT NULL DEFAULT 0,
    fecha_inicio TIMESTAMP,
    fecha_fin TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_historico_documento_id ON historico_documentos(documento_id);
CREATE INDEX idx_historico_doc_operacion ON historico_documentos(documento_id, operacion, fecha_creacion DESC);
```

---

## Decisiones Clave

| Decision | Detalle |
|----------|---------|
| SYNCED + PENDING | Se unifican en un solo estado `PENDING` |
| Columnas no usadas | Se conservan en `documentos` (`activo`, `clave_documento`, `propietario`, `ruta`, `version_contrato`) pero se **eliminan** de `historico_documentos` y de `DocumentHistory.java` |
| Relacion FK | Numerica: `historico_documentos.documento_id` → `documentos.id` |
| `documentId` en DocumentHistory | Cambia de `String` (business key) a `Long` (FK numerico) |
| `nombre_archivo` en historico | Campo nuevo para trazabilidad directa sin JOIN |

---

## Impacto en Capa de Dominio

### Nuevo: `Document.java`
Record con 16 campos de metadata que mapea directamente a la tabla `documentos`.

### Reescritura: `DocumentHistory.java`
Se reduce de 22 a 13 campos, **todos activamente usados** para trazabilidad. `documentId` cambia de `String` a `Long`. Se agrega `filename` para saber que archivo se valido/envio sin necesidad de joinear con `documentos`. Los campos no usados (`activo`, `clave_documento`, `ruta`, `version_contrato`) se **eliminan del codigo fuente**, no solo se mueven a `Document`.

### Nuevo puerto: `DocumentRepository.java`
```java
Mono<Document> save(Document document);
Flux<Document> findByStateAndUseCase(String state, String useCase);
Mono<Void> updateStateById(Long id, String state, LocalDateTime updatedAt);
```

### Puerto simplificado: `DocumentHistoryRepository.java`
Se reduce de 5 a 2 metodos:
```java
Mono<Void> save(DocumentHistory history);
Mono<DocumentHistory> findLastAudit(Long documentId, String useCase);  // Long, no String
```

---

## Impacto en Casos de Uso

### `AbstractDocumentProcessingUseCase`
- Se inyecta `DocumentRepository` adicional a `DocumentHistoryRepository`
- `executePendingDocuments()` consulta `documentRepository.findByStateAndUseCase()` y retorna `Flux<Document>`
- `canResume()` usa `documentRepository.findLastAudit(doc.id(), ...)` con el ID numerico
- `startProcessing()` → `documentRepository.updateStateById(doc.id(), IN_PROGRESS, now)`
- `handleUploadSuccess()` → `documentRepository.updateStateById()` + `historyRepository.save()` (traza SUCCESS)
- `handleUploadError()` → `historyRepository.findLastAudit()` para conteo de retry + `documentRepository.updateStateById()` + `historyRepository.save()` (traza FAILURE)

### `SyncDocumentsUseCase`
El metodo `saveDocument()` ahora persiste en **dos tablas**:
1. `documentRepository.save(document)` — inserta la metadata con `estado=PENDING`
2. Sobre el resultado (con el ID generado), `historyRepository.save(trace)` — inserta trazabilidad con `operacion="SYNC"`, `resultado="SUCCESS"`

### `SoapDocumentProcessingUseCase` / `S3DocumentProcessingUseCase`
Solo cambia el constructor: se agrega `DocumentRepository` como primer parametro.

---

## Archivos a Crear (6)

| # | Archivo |
|---|---------|
| 1 | `src/main/java/.../domain/entity/Document.java` |
| 2 | `src/main/java/.../domain/port/out/DocumentRepository.java` |
| 3 | `src/main/java/.../infrastructure/.../r2dbc/entity/DocumentEntity.java` |
| 4 | `src/main/java/.../infrastructure/.../r2dbc/mapper/DocumentMapper.java` |
| 5 | `src/main/java/.../infrastructure/.../r2dbc/DocumentR2dbcAdapter.java` |
| 6 | `src/test/java/.../infrastructure/.../r2dbc/DocumentR2dbcAdapterTest.java` |

## Archivos a Modificar (14)

| # | Archivo | Tipo de cambio |
|---|---------|----------------|
| 1 | `domain/entity/DocumentHistory.java` | Reescritura: 13 campos, `documentId` Long, agrega `filename` |
| 2 | `domain/entity/ProductState.java` | Eliminar constante `SYNCED` |
| 3 | `domain/port/out/DocumentHistoryRepository.java` | Simplificar de 5 a 2 metodos |
| 4 | `domain/usecase/AbstractDocumentProcessingUseCase.java` | Inyectar `DocumentRepository`, usar `Document` para metadata |
| 5 | `domain/usecase/SyncDocumentsUseCase.java` | Guardar en 2 tablas |
| 6 | `domain/usecase/SoapDocumentProcessingUseCase.java` | Constructor: + `DocumentRepository` |
| 7 | `domain/usecase/S3DocumentProcessingUseCase.java` | Constructor: + `DocumentRepository` |
| 8 | `application/service/config/DomainConfig.java` | Wire `DocumentRepository` |
| 9 | `infrastructure/.../entity/DocumentHistoryEntity.java` | Reescritura: solo columnas trazabilidad |
| 10 | `infrastructure/.../mapper/DocumentHistoryMapper.java` | Mapear solo 13 campos |
| 11 | `infrastructure/.../DocumentHistoryR2dbcAdapter.java` | Implementar interfaz 2-metodos |
| 12 | `infrastructure/.../repository/DocumentHistoryRepository.java` | Actualizar `@Query` (nuevas columnas) |
| 13 | `resources/schema.sql` + `resources/schema-postgresql.sql` | Agregar `documentos`, reescribir `historico_documentos` |
| 14 | `README.md` | Actualizar diagramas, documentacion de tablas, queries, reglas de negocio |

### Tests a modificar (4)

| # | Archivo |
|---|---------|
| 1 | `test/.../r2dbc/DocumentHistoryR2dbcAdapterTest.java` |
| 2 | `test/.../usecase/SyncDocumentsUseCaseTest.java` |
| 3 | `test/.../usecase/SoapDocumentProcessingUseCaseTest.java` |
| 4 | `test/.../usecase/S3DocumentProcessingUseCaseTest.java` |

### Tests que NO cambian
`ProductDocumentHistoryTest`, `DocumentValidatorTest`, `ZipDecompressorTest`, `ProductRestGatewayAdapterTest` — no dependen de entidades de DB.

---

## Secuencia de Implementacion

### Fase 1: Dominio (bottom-up)
1. Crear `Document.java`
2. Reescribir `DocumentHistory.java` (13 campos, agrega `filename`)
3. Eliminar `SYNCED` de `ProductState.java`
4. Crear `DocumentRepository.java` (puerto)
5. Simplificar `DocumentHistoryRepository.java`

### Fase 2: Infraestructura
6. Crear `DocumentEntity.java`
7. Crear `DocumentMapper.java`
8. Crear Spring Data `DocumentRepository.java`
9. Crear `DocumentR2dbcAdapter.java`
10. Reescribir `DocumentHistoryEntity.java`
11. Reescribir `DocumentHistoryMapper.java`
12. Actualizar queries en Spring Data `DocumentHistoryRepository.java`
13. Reescribir `DocumentHistoryR2dbcAdapter.java`

### Fase 3: Casos de uso + config
14. Actualizar `AbstractDocumentProcessingUseCase.java`
15. Actualizar `SyncDocumentsUseCase.java`
16. Actualizar `SoapDocumentProcessingUseCase.java` (constructor)
17. Actualizar `S3DocumentProcessingUseCase.java` (constructor)
18. Actualizar `DomainConfig.java`

### Fase 4: Schemas SQL
19. Actualizar `schema.sql` (H2)
20. Actualizar `schema-postgresql.sql` (PostgreSQL)
21. Crear migration `docs/migrations/005_split_documentos_tables.sql`

### Fase 5: Tests
22. Crear `DocumentR2dbcAdapterTest.java`
23-26. Actualizar los 4 tests existentes

### Fase 6: Documentacion
27. Actualizar README.md

---

## Analisis de Flujos: Estados y Trazabilidad

### Maquina de Estados — `documentos.estado`

```
                         ┌──────────────┐
                         │   PENDING    │  ← documento listo para procesar
                         │ (desde Sync) │     (unifica SYNCED + PENDING)
                         └──────┬───────┘
                                │
                                │ executePendingDocuments()
                                ▼
                         ┌──────────────┐
                         │ IN_PROGRESS  │  ← en procesamiento actual
                         └──────┬───────┘
                                │
                    ┌───────────┼───────────┐
                    │           │           │
                    ▼           ▼           ▼
             ┌──────────┐ ┌──────────┐ ┌──────────┐
             │PROCESSED │ │ PENDING  │ │  FAILED  │
             │(exito o  │ │(retry    │ │(agoto    │
             │ skip)    │ │  < 3)    │ │ intentos)│
             └──────────┘ └──────────┘ └──────────┘
```

| Estado | Significado | Disparador |
|--------|-------------|------------|
| `PENDING` | Documento listo para ser procesado | Sync inicial o fallo con retry < 3 |
| `IN_PROGRESS` | En procesamiento actual | Se asigna justo antes de `processDocument()` |
| `PROCESSED` | Enviado exitosamente O skip por validacion | Upload OK o documento no pasa reglas de negocio |
| `FAILED` | Agoto reintentos (3) o fallo permanente | Error con `retry >= 3` o error no reintentable |

---

### Trazabilidad — `historico_documentos`

Cada evento significativo durante el **procesamiento** del documento deja una fila en `historico_documentos`. Esta tabla es **append-only**: nunca se actualiza, solo se inserta.

> **Nota:** Durante SYNC (`POST /api/v1/products/sync`) NO se guarda en `historico_documentos`. Solo se persisten los metadatos en `documentos`. La trazabilidad comienza cuando el documento se procesa via SOAP o S3.

| Campo | Valores posibles |
|-------|-----------------|
| `nombre_archivo` | Nombre del archivo descomprimido (ej. `data.csv`). Solo se populate para entradas de ZIP (`parentZipName != null`). Para documentos originales es `NULL` |
| `operacion` | `SOAP`, `S3` (NO se graba durante SYNC) |
| `resultado` | `SUCCESS`, `FAILURE` |
| `codigo_error` | `null` (si SUCCESS), `GATEWAY_TIMEOUT`, `BAD_GATEWAY`, `SERVICE_UNAVAILABLE`, `UNKNOWN_ERROR`, `INVALID_ZIP`, `REST_CLIENT_ERROR`, `BUSINESS_RULE_SKIP`, `EMPTY_CONTENT` |
| `reintentos` | `0` = primer intento, `1` = primer reintento, `2` = segundo, etc. |

---

### Flujo 1: Sync Inicial (POST /api/v1/products/sync)

```
ProductHandler.syncProducts()
  → SyncDocumentsUseCase.execute(useCase, messageId)
    → productRepository.findAll()
      → productRestGateway.getDocumentsByProduct(product)
        → documentRepository.save(document)
```

| Paso | Tabla | Accion |
|------|-------|--------|
| 1 | `documentos` | `INSERT`: `id_documento`, `id_producto`, `nombre`, `propietario`, `es_zip`, `estado=PENDING`, `caso_uso`=useCase |

**Resultado:**
- `documentos.estado` = `PENDING`
- `historico_documentos`: **NO se inserta ninguna fila** (la trazabilidad comienza en el procesamiento)

> **Nota:** El campo `es_zip` se setea durante el sync segun la extension del archivo. La descompresion y validacion ocurre unicamente durante el procesamiento (`GET /api/v1/products`).

---

### Flujo 2: Procesamiento Exitoso (Happy Path)

```
GET /api/v1/products?processor=soap
  → AbstractDocumentProcessingUseCase.executePendingDocuments()
```

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| 1 | Buscar pendientes | `documentos` | `SELECT ... WHERE estado=PENDING AND caso_uso=SOAP` |
| 2 | canResume | `historico_documentos` | `findLastAudit(documento.id, "SOAP")` → verifica que no este PROCESSED |
| 3 | Marcar inicio | `documentos` | `UPDATE estado=IN_PROGRESS, fecha_actualizacion=now` |
| 4 | Obtener archivo | REST API | `GET /api/products/{productId}/documents/{docId}` |
| 5 | Descomprimir ZIP | (memoria) | Si `es_zip=true`, expande cada entrada |
| 6 | Validar reglas | (memoria) | Tamano ≤ max, nombre coincide con patron |
| 7 | Enviar a gateway | SOAP/S3 | `soapGateway.send(request)` o `s3Gateway.send(request)` |
| 8a | Exito envio | `documentos` | `UPDATE estado=PROCESSED, fecha_actualizacion=now` |
| 8b | Exito envio | `historico_documentos` | `INSERT`: `documento_id`, `nombre_archivo`, `operacion=SOAP`, `resultado=SUCCESS`, `reintentos=0`, `fecha_fin=now` |

**Resultado:**
- `documentos.estado` = `PROCESSED`
- `historico_documentos`: +1 fila con `operacion=SOAP/S3`, `resultado=SUCCESS`, `reintentos=0`

---

### Flujo 3: Skip por Reglas de Negocio (extension o peso)

Igual que Flujo 2 hasta el paso 6, donde la validacion falla.

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| 6 | Validar reglas | (memoria) | `filename` no coincide con `.*\.(pdf|docx|txt)$` o `size > max` |
| 7 | Skip | `documentos` | `UPDATE estado=PROCESSED, mensaje_error="Skipped by business rule: ..."` |
| 8 | Skip | `historico_documentos` | `INSERT`: `documento_id`, `nombre_archivo`, `operacion=SOAP/S3`, `resultado=FAILURE`, `codigo_error=BUSINESS_RULE_SKIP`, `fecha_fin=now` |

**Resultado:**
- `documentos.estado` = `PROCESSED` (no se reintenta — el skip es definitivo)
- `historico_documentos`: +1 fila con `resultado=FAILURE`, `codigo_error=BUSINESS_RULE_SKIP`

**Registro del motivo del skip:**

| Condicion | `mensaje_error` en documentos |
|-----------|------------------------------|
| Extension no permitida | `"Skipped: filename 'archive.exe' does not match pattern .*\\.(pdf|docx|txt)$"` |
| Tamano excedido | `"Skipped: file size 15MB exceeds max 10MB"` |

---

### Flujo 4: Fallo de Descompresion ZIP

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| 5 | Descomprimir | `ZipDecompressor` | Lanza `ProcessingException(INVALID_ZIP)` |
| 6 | Error handler | `historico_documentos` | `findLastAudit(doc.id, useCase)` para contar reintentos |
| 7a | Actualizar estado | `documentos` | `UPDATE estado=FAILED, mensaje_error="Corrupt ZIP: ..."` |
| 7b | Traza fallo | `historico_documentos` | `INSERT`: `documento_id`, `nombre_archivo`, `operacion=SOAP/S3`, `resultado=FAILURE`, `codigo_error=INVALID_ZIP`, `stack_trace` | |

**Resultado:**
- `documentos.estado` = `FAILED` (ZIP corrupto no se reintenta — es un fallo permanente)
- `historico_documentos`: +1 fila con `resultado=FAILURE`, `codigo_error=INVALID_ZIP`

---

### Flujo 5: Fallo de Gateway con Reintento (retry < 3)

Ejemplo: timeout en SOAP, primer intento fallido.

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| 7 | Enviar | SOAP | `TimeoutException` tras 30s |
| 8 | Error handler | `historico_documentos` | `findLastAudit(doc.id, "SOAP")` → `retry=0` (primer intento) |
| 9 | Calcular retry | (memoria) | `retry = 0 + 1 = 1`. `retry < 3` → `newState = PENDING` |
| 10a | Actualizar estado | `documentos` | `UPDATE estado=PENDING, mensaje_error="Timeout after 30s"` |
| 10b | Traza fallo | `historico_documentos` | `INSERT`: `documento_id`, `operacion=SOAP`, `resultado=FAILURE`, `codigo_error=GATEWAY_TIMEOUT`, `reintentos=1`, `stack_trace`, `fecha_fin=now` |

**Resultado:**
- `documentos.estado` = `PENDING` (volvera a intentarse en la siguiente ejecucion)
- `historico_documentos`: +1 fila con `resultado=FAILURE`, `codigo_error=GATEWAY_TIMEOUT`, `reintentos=1`

En la siguiente ejecucion, `executePendingDocuments()` encontrara el documento en PENDING y lo procesara de nuevo (segundo intento, `retry=2` si falla).

---

### Flujo 6: Fallo de Gateway Agotando Reintentos (retry >= 3)

Tercer intento fallido consecutivo.

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| 8 | Error handler | `historico_documentos` | `findLastAudit(doc.id, "SOAP")` → `retry=2` (segundo reintento previo) |
| 9 | Calcular retry | (memoria) | `retry = 2 + 1 = 3`. `retry >= 3` → `newState = FAILED` |
| 10a | Actualizar estado | `documentos` | `UPDATE estado=FAILED, mensaje_error="Timeout after 30s"` |
| 10b | Traza fallo | `historico_documentos` | `INSERT`: `documento_id`, `operacion=SOAP`, `resultado=FAILURE`, `codigo_error=GATEWAY_TIMEOUT`, `reintentos=3`, `stack_trace` |

**Resultado:**
- `documentos.estado` = `FAILED` (no se reintentara mas automaticamente)
- `historico_documentos`: +1 fila con `resultado=FAILURE`, `reintentos=3`

---

### Flujo 7: Fallo al Obtener Documento desde REST API

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| 4 | Obtener archivo | REST API | `WebClientResponseException` 500 |
| 5 | Error handler | `historico_documentos` | `findLastAudit` para conteo de retry |
| 6a | Actualizar estado | `documentos` | `UPDATE estado=PENDING/FAILED` (segun retry count) |
| 6b | Traza fallo | `historico_documentos` | `INSERT`: `resultado=FAILURE`, `codigo_error=REST_CLIENT_ERROR` |

---

### Flujo 8: Contenido Vacio o Base64 Invalido

Este flujo ocurre durante el sync — no durante el procesamiento.

| # | Paso | Tabla | Accion |
|---|------|-------|--------|
| - | Sync | REST API | `Base64Utils.decodeSafe()` lanza `InvalidBase64Exception(EMPTY_CONTENT)` o `InvalidBase64Exception(INVALID_BASE64)` |
| - | Error | `documentos` | `INSERT`: `estado=FAILED`, `mensaje_error`, `codigo_error`=EMPTY_CONTENT/INVALID_BASE64 |
| - | Error | `historico_documentos` | `INSERT`: `operacion=SYNC`, `resultado=FAILURE`, `codigo_error` |

---

### Tabla Resumen de Todos los Escenarios

| # | Escenario | Estado Final `documentos` | `historico_documentos.resultado` | `codigo_error` | ¿Reintentable? |
|---|-----------|--------------------------|--------------------------------|----------------|----------------|
| 1 | Sync exitoso | `PENDING` | `SUCCESS` | — | N/A (aun no se procesa) |
| 2 | Envio exitoso | `PROCESSED` | `SUCCESS` | — | No (terminado) |
| 3 | Skip por reglas | `PROCESSED` | `FAILURE` | `BUSINESS_RULE_SKIP` | No (skip definitivo) |
| 4 | ZIP corrupto | `FAILED` | `FAILURE` | `INVALID_ZIP` | No (fallo permanente) |
| 5 | Gateway fallo + retry < 3 | `PENDING` | `FAILURE` | `GATEWAY_TIMEOUT` | Si (proximo ciclo) |
| 6 | Gateway fallo + retry >= 3 | `FAILED` | `FAILURE` | `GATEWAY_TIMEOUT` | No (agoto intentos) |
| 7 | REST API fallo | `PENDING` o `FAILED` | `FAILURE` | `REST_CLIENT_ERROR` | Segun retry count |
| 8 | Base64 invalido/vacio | `FAILED` | `FAILURE` | `EMPTY_CONTENT` / `INVALID_BASE64` | No |

---

### Ejemplo de Trazabilidad Completa

Escenario real: Producto `PROD-001` con 3 documentos — uno es un ZIP que al procesarse genera 2 entradas, y hay un documento independiente.

**Contexto:**
- `bundle.zip` (id=10): contiene `manual.pdf` y `specs.docx`
- `invoice.pdf` (id=11): documento independiente
- El endpoint SOAP tiene timeout intermitentes

**Durante SYNC** (`POST /api/v1/products/sync`): Solo se persisten en `documentos`:

```
┌────┬─────────────────────────┬─────────────┬─────────────┬──────────┬──────────┬───────────┐
│ id │ id_documento            │ id_producto │ nombre       │ es_zip   │ estado   │ caso_uso  │
├────┼─────────────────────────┼─────────────┼─────────────┼──────────┼──────────┼───────────┤
│ 10 │ PROD-001/bundle.zip     │ PROD-001    │ bundle.zip  │ true     │ PENDING  │ SOAP      │
│ 11 │ PROD-001/invoice.pdf    │ PROD-001    │ invoice.pdf │ false    │ PENDING  │ SOAP      │
└────┴─────────────────────────┴─────────────┴─────────────┴──────────┴──────────┴───────────┘
```

`historico_documentos`: **NO se inserta ninguna fila durante SYNC**

---

#### Procesamiento (`GET /api/v1/products?processor=soap`)

El documento `bundle.zip` (id=10) se descomprime en runtime generando 2 entradas. Cada entrada se procesa independientemente y deja su traza en `historico_documentos`.

**`historico_documentos`** — solo entradas de procesamiento (SOAP/S3):

```
┌─────┬───────────────┬────────────────────┬───────────┬───────────┬──────────┬──────────────────────┬──────────────────────────────────────────┬───────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┐
│ id  │ documento_id  │ nombre_archivo     │ operacion │ resultado │ cod_error│ mensaje_error        │ stack_trace                              │ reintentos│ fecha_inicio            │ fecha_fin               │ fecha_creacion          │
├─────┼───────────────┼────────────────────┼───────────┼───────────┼──────────┼──────────────────────┼──────────────────────────────────────────┼───────────┼─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ 101 │ 10            │ manual.pdf         │ SOAP      │ SUCCESS   │ null     │ null                 │ null                                     │ 0         │ 2026-05-07 08:05:10     │ 2026-05-07 08:05:10     │ 2026-05-07 08:05:10     │ ← bundle.zip: entrada manual.pdf OK
│ 102 │ 10            │ specs.docx         │ SOAP      │ FAILURE   │GATEWAY_..│ Timeout after 30s    │ java.util.concurrent.TimeoutException    │ 1         │ 2026-05-07 08:05:10     │ 2026-05-07 08:05:40     │ 2026-05-07 08:05:40     │ ← bundle.zip: entrada specs.docx intento 1
│     │               │                    │           │           │          │                      │   at reactor.core.publisher...           │           │                         │                         │                         │
│ 103 │ 10            │ specs.docx         │ SOAP      │ FAILURE   │GATEWAY_..│ Timeout after 30s    │ java.util.concurrent.TimeoutException    │ 2         │ 2026-05-07 08:15:05     │ 2026-05-07 08:15:35     │ 2026-05-07 08:15:35     │ ← specs.docx intento 2
│     │               │                    │           │           │          │                      │   at reactor.core.publisher...           │           │                         │                         │                         │
│ 104 │ 10            │ specs.docx         │ SOAP      │ FAILURE   │GATEWAY_..│ Timeout after 30s    │ java.util.concurrent.TimeoutException    │ 3         │ 2026-05-07 08:25:12     │ 2026-05-07 08:25:42     │ 2026-05-07 08:25:42     │ ← specs.docx intento 3 (agotado)
│     │               │                    │           │           │          │                      │   at reactor.core.publisher...           │           │                         │                         │                         │
│ 105 │ 11            │ NULL               │ SOAP      │ FAILURE   │BUSINESS_.│ Skipped: filename    │ null                                     │ 0         │ 2026-05-07 08:05:11     │ 2026-05-07 08:05:11     │ 2026-05-07 08:05:11     │ ← invoice.pdf skip (documento original)
│     │               │                    │           │           │          │ 'invoice.pdf' does   │                                          │           │                         │                         │                         │
│     │               │                    │           │           │          │ not match pattern    │                                          │           │                         │                         │                         │
└─────┴───────────────┴────────────────────┴───────────┴───────────┴──────────┴──────────────────────┴──────────────────────────────────────────┴───────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┘
```

**Regla para `nombre_archivo`:**

| Caso | `nombre_archivo` | Motivo |
|------|-----------------|--------|
| Entrada de ZIP (`manual.pdf` extraida de `bundle.zip`) | `manual.pdf` | `parentZipName=bundle.zip`. Identifica que entrada del ZIP se proceso |
| Entrada de ZIP (`specs.docx` extraida de `bundle.zip`) | `specs.docx` | `parentZipName=bundle.zip`. Identifica que entrada del ZIP se proceso |
| Documento original (`invoice.pdf`) | `NULL` | No proviene de ZIP. El nombre ya esta en `documentos.nombre` |

#### Lectura de la Trazabilidad por Documento

**`manual.pdf` (id=10, entrada de `bundle.zip`) — Exito en primer intento:**

```
PENDING ──► IN_PROGRESS ──► ZipDecompressor ──► upload SOAP ──► PROCESSED
                                              │
                                              └── historico: nombre_archivo=manual.pdf, operacion=SOAP, resultado=SUCCESS, reintentos=0
```

Resultado: 1 fila en `historico_documentos` (solo el envio SOAP — SYNC no crea traza).

---

**`specs.docx` (id=10, entrada de `bundle.zip`) — Fallo con 3 reintentos agotados:**

```
PENDING ──► IN_PROGRESS ──► ZipDecompressor ──► upload SOAP ──► TimeoutException
                                                              │
                                                              ▼
                                                   findLastAudit(10, "SOAP") → retry=0
                                                   nuevoRetry = 0+1 = 1 (< 3)
                                                   estado → PENDING
                                                   traza → nombre_archivo=specs.docx, FAILURE, reintentos=1
                                                              │
                                                              ▼ (siguiente ejecucion)
                                                   PENDING ──► IN_PROGRESS ──► upload SOAP ──► TimeoutException
                                                                                                  │
                                                                                                  ▼
                                                                                       findLastAudit → retry=1
                                                                                       nuevoRetry = 1+1 = 2 (< 3)
                                                                                       estado → PENDING
                                                                                       traza → nombre_archivo=specs.docx, FAILURE, reintentos=2
                                                                                                  │
                                                                                                  ▼ (siguiente ejecucion)
                                                                                       PENDING ──► IN_PROGRESS ──► upload SOAP ──► TimeoutException
                                                                                                                              │
                                                                                                                              ▼
                                                                                                                   findLastAudit → retry=2
                                                                                                                   nuevoRetry = 2+1 = 3 (>= 3)
                                                                                                                   estado → FAILED
                                                                                                                   traza → nombre_archivo=specs.docx, FAILURE, reintentos=3
```

Resultado: 4 filas en `historico_documentos` (todas de intentos SOAP). SYNC no crea traza.
`documentos.estado` = `FAILED`. No se volvera a procesar automaticamente.

---

**`invoice.pdf` (id=11, documento independiente) — Skip por regla de negocio:**

```
PENDING ──► IN_PROGRESS ──► validar reglas ──► filename "invoice.pdf" NO coincide con ".*\\.(pdf|docx|txt)$"
                                                 │
                                                 ▼
                                      estado → PROCESSED (skip definitivo)
                                      traza → nombre_archivo=NULL, FAILURE, BUSINESS_RULE_SKIP
```

Resultado: 1 fila en `historico_documentos` (solo el skip — SYNC no crea traza).
`documentos.estado` = `PROCESSED`. No se reintenta — el skip es definitivo.

**Nota importante:** `PROCESSED` se usa tanto para envio exitoso como para skip. La diferencia se ve en `historico_documentos`:
- Envio exitoso → `resultado=SUCCESS`, `codigo_error=null`
- Skip → `resultado=FAILURE`, `codigo_error=BUSINESS_RULE_SKIP`


**`fecha_inicio` vs `fecha_fin`:** Para SYNC son iguales (operacion instantanea). Para SOAP/S3 con timeout, `fecha_fin - fecha_inicio` = duracion del intento (ej. 30s timeout). Para skip por regla, son iguales (validacion instantanea).



#### Consultas Utiles para Trazabilidad

```sql
-- Ver el estado actual de todos los documentos de un producto
SELECT * FROM documentos WHERE id_producto = 'PROD-001';

-- Ver la trazabilidad completa de un documento especifico
SELECT * FROM historico_documentos WHERE documento_id = 10 ORDER BY fecha_creacion;

-- Ultimo evento de cada documento (para saber si reintentar)
SELECT DISTINCT ON (documento_id)
    documento_id, nombre_archivo, operacion, resultado, codigo_error, reintentos, fecha_inicio, fecha_fin, fecha_creacion
FROM historico_documentos
ORDER BY documento_id, fecha_creacion DESC;

-- Documentos que requieren atencion (FAILED o con muchos reintentos)
SELECT d.*, h.resultado, h.reintentos
FROM documentos d
JOIN historico_documentos h ON h.documento_id = d.id
WHERE d.estado = 'FAILED'
ORDER BY h.fecha_creacion DESC;

-- Conteo de exitos vs fallos por caso de uso
SELECT operacion, resultado, COUNT(*)
FROM historico_documentos
GROUP BY operacion, resultado
ORDER BY operacion, resultado;
```

### Codigos de Error

| Codigo | Disparador | ¿Reintentable? |
|--------|-----------|----------------|
| `BUSINESS_RULE_SKIP` | Extension no permitida o tamano excedido | No |
| `INVALID_ZIP` | Archivo ZIP corrupto | No |
| `EMPTY_CONTENT` | Contenido Base64 null o vacio | No |
| `INVALID_BASE64` | Base64 mal formado | No |
| `GATEWAY_TIMEOUT` | Timeout en llamada SOAP/S3 | Si |
| `BAD_GATEWAY` | HTTP 500 del servicio externo | No |
| `SERVICE_UNAVAILABLE` | Connection refused al servicio externo | Si |
| `REST_CLIENT_ERROR` | Fallo al obtener documento de REST API | Si |
| `UNKNOWN_ERROR` | Cualquier otra excepcion | Si |

---

## Riesgos

| Riesgo | Mitigacion |
|--------|------------|
| `documentId` cambia de `String` a `Long` en `DocumentHistory` | Verificar cada referencia en el codigo. Solo se usa en use cases y sus tests |
| Constructor de use cases cambia firma | Spring DI lo resuelve automaticamente. Solo los tests necesitan ajuste manual |
| Datos existentes en produccion | La migration `005` documenta el proceso de migracion. Requiere coordinacion con DBA |
| H2 vs PostgreSQL diferencias | Ambas ya tienen schemas separados. Se actualizan en paralelo |

---

## Verificacion

```bash
./gradlew compileJava      # Debe compilar sin errores
./gradlew test             # Todos los tests deben pasar (196+)
./gradlew pitest           # Umbral de mutacion: 60%
./gradlew jacocoTestReport # Umbral de cobertura: 75%
```
