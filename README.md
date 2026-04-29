# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envía a un servicio SOAP externo o AWS S3.

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas claras:

```
com.example.fileprocessor/
├── domain/                          # Capa de dominio (puro Java, sin frameworks)
│   ├── entity/                      # Entidades de negocio
│   │   ├── ProductToProcess.java            # Producto en BD
│   │   ├── ProductDocumentToProcess.java    # Documento de producto en BD
│   │   ├── ProductInfo.java                 # Producto desde REST API
│   │   ├── ProductDocumentInfo.java        # Documento dentro de ProductInfo
│   │   ├── FileUploadResult.java           # Resultado de upload/procesamiento
│   │   ├── ExternalServiceResponse.java    # Respuesta generica de servicio externo
│   │   ├── DocumentSendRequest.java       # Request de envio
│   │   ├── ZipArchive.java                 # ZIP con proteccion contra bombs
│   │   ├── AsyncOperationStatus.java      # Estado de operacion async
│   │   ├── DocumentStatus.java            # Estados de documento (constantes)
│   │   └── ProductStatus.java             # Estados de producto
│   ├── usecase/                      # Casos de uso
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base
│   │   ├── SoapDocumentProcessingUseCase.java      # Implementacion SOAP
│   │   ├── S3DocumentProcessingUseCase.java        # Implementacion S3
│   │   ├── LoadProductsUseCase.java                # Carga productos y documentos
│   │   ├── LoadProductsResult.java                 # Resultado de carga
│   │   ├── FileValidator.java                      # Validador de archivos
│   │   ├── ProductStatusAggregator.java            # Agregacion de estado
│   │   ├── ProductStatusSummary.java               # Resumen de estado
│   │   ├── ProcessingCheckpoint.java              # Checkpoint de procesamiento
│   │   ├── CommunicationLogFactory.java            # Factory para logs
│   │   └── ProcessingResultCodes.java              # Codigos de resultado
│   ├── util/
│   │   ├── Base64Utils.java                  # Encoding/decoding Base64
│   │   └── MediaTypeConstants.java           # Constantes de tipos MIME
│   ├── valueobject/
│   │   ├── FolderExclusionRegexConfig.java   # Patrones regex (fail-fast)
│   │   └── ProcessingDependencies.java       # Record de dependencias compartidas
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java     # Configuracion de validacion
│   │   └── out/
│   │       ├── ProductRestGateway.java      # Puerto REST productos
│   │       ├── ProductRepository.java        # Puerto productos
│   │       ├── ProductDocumentRepository.java # Puerto documentos
│   │       ├── FileGateway.java              # Puerto unificado (SOAP/S3)
│   │       └── CommunicationLogRepository.java # Puerto para logs
│   └── exception/
│       ├── DomainException.java
│       ├── FileValidationException.java
│       └── CommunicationException.java
│
├── application/                      # Configuracion de aplicacion
│   └── app-service/
│       └── config/
│           ├── DomainConfig.java           # Beans de casos de uso
│           └── DatabaseInitializer.java   # Inicializacion de BD
│
└── infrastructure/                  # Capa de infraestructura
    ├── entrypoints/
    │   └── rest/
    │       ├── ProductRoutes.java         # RouterFunction
    │       ├── handler/
    │       │   └── ProductHandler.java     # Logica de handlers
    │       ├── config/
    │       │   └── DocumentRestProperties.java
    │       └── constants/
    │           ├── RestApiPaths.java
    │           └── ApiConstants.java      # message-id header
    ├── drivenadapters/
    │   ├── rest-client/
    │   │   └── ProductRestGatewayAdapter.java
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java
    │   │   └── config/
    │   │       └── SoapProperties.java
    │   ├── aws/
    │   │   ├── S3GatewayAdapter.java     # Con retry y sanitizacion
    │   │   └── config/
    │   │       ├── AwsConfig.java
    │   │       └── S3Properties.java
    │   └── r2dbc/
    │       ├── R2dbcProductRepository.java
    │       └── R2dbcProductDocumentRepository.java
    └── helpers/
        ├── config/
        │   └── ProcessorConfig.java      # Config unificada procesadores
        ├── soap/
        │   ├── mapper/
        │   │   └── SoapMapper.java
        │   ├── xml/
        │   │   ├── SoapEnvelopeWrapper.java
        │   │   ├── SoapNamespaces.java
        │   │   └── model/
        │   │       ├── UploadFileRequest.java
        │   │       └── UploadFileResponse.java
        │   └── exception/
        │       └── SoapCommunicationException.java
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa (puro Java)
- **Domain** no contiene anotaciones Spring (`@Component`, etc.)
- **Infrastructure** y **Application** dependen de Domain
- **Application** expone los beans via `DomainConfig.java`

---

## Flujo de Procesamiento de Documentos

### Diagrama General

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           API Request                                       │
│                      GET /api/v1/products?processor=soap                    │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductHandler.processPendingProducts()                                  │
│    - Valida processor type (soap|s3)                                       │
│    - Genera traceId UUID si no existe en header                            │
│    - Retorna HTTP 202 ACCEPTED inmediatamente                               │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 2. AbstractDocumentProcessingUseCase.executePendingDocuments()             │
│    - findPendingDocuments() → Flux<ProductDocumentToProcess>              │
│    - flatMap con processPendingDocument()                                   │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 3. processPendingDocument() - Claim Atomico                                  │
│                                                                              │
│    claimDocument(documentId)                                                 │
│    ┌────────────────────────────────────────────────────────────────────┐   │
│    │ UPDATE product_documents_to_process                                  │   │
│    │ SET status = 'PROCESSING'                                           │   │
│    │ WHERE document_id = $1 AND status IN ('PENDING', 'RETRY')           │   │
│    └────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│    Si rowsUpdated > 0 → documento clonado, continuar                        │
│    Si rowsUpdated = 0 → otro pod lo tomo, skip                              │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
         ┌──────────────────┐           ┌──────────────────────────────┐
         │ Documento YA    │           │ Documento NECESITA          │
         │ tiene content   │           │ downloadContentIfNeeded()    │
         │ (pre-cargado)   │           │ - GET /products/{id}/docs/{id}│
         └────────┬─────────┘           │ - Decode Base64              │
                  │                     │ - updateContent()            │
                  │                     └──────────────┬───────────────┘
                  │                                    │
                  └──────────────┬─────────────────────┘
                                 ▼
                   ┌─────────────────────────────┐
                   │ isZipArchive?              │
                   ├─────────────────────────────┤
                   │ true  → processZipDocument │
                   │ false → processDocumentInternal│
                   └─────────────────────────────┘
```

### Process ZIP Document (Desglose)

Cuando un documento es ZIP, se extrae y procesa cada hijo:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ processZipDocument(zipDoc, traceId)                                          │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 3.1 extractZipChildren()                                                   │
│                                                                              │
│    ZipArchive.builder()                                                      │
│        .zipContent(bytes)                                                    │
│        .originalFilename(name)                                               │
│        .maxEntries(1000)           ← Proteccion contra ZIP bombs             │
│        .maxUncompressedSize(100MB)                                          │
│        .build()                                                               │
│                                                                              │
│    archive.extractDocuments()                                                │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ Validaciones durante extraccion:                                  │    │
│    │ - Contador de entries <= maxEntries (1000)                       │    │
│    │ - Tamano descomprimido por entry <= maxUncompressedSize          │    │
│    │ - Tamano total acumulado <= maxUncompressedSize                  │    │
│    │                                                                     │    │
│    │ Si falla: FileValidationException ZIP_EXTRACTION_FAILED          │    │
│    └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│    Casos:                                                                   │
│    - ZIP valido con hijos → retorna lista de ExtractedDocument             │
│    - ZIP vacio → retorna lista vacia []                                   │
│    - ZIP corrupto/invalido → exception → status=FAILURE                   │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
         ┌──────────────────┐           ┌──────────────────────────────┐
         │ children.isEmpty│           │ children.isNotEmpty         │
         │ handleEmptyZip()│           │ processZipChildren()        │
         └──────────────────┘           └──────────────────────────────┘
```

#### 3.1.1 handleEmptyZip() - ZIP Vacio

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ handleEmptyZip(zipDoc, traceId)                                             │
│                                                                              │
│ 1. Log: "ZIP {filename} is empty"                                          │
│ 2. UPDATE document status → SUCCESS                                        │
│ 3. RETURN FileUploadResult                                                   │
│    - status: SUCCESS                                                        │
│    - correlationId: zipDoc.documentId                                     │
│    - message: "ZIP was empty"                                              │
│                                                                              │
│ Consideracion: Un ZIP vacio no es error, es valido.                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 3.1.2 processZipChildren() - Procesar Hijos

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ processZipChildren(zipDoc, children, traceId)                             │
│                                                                              │
│ Flux.fromIterable(children)                                                 │
│     .flatMapSequential(extracted → processZipChild())                      │
│     .collectList()                                                          │
│     .flatMap(results → aggregateZipResults())                              │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ processZipChild(zipDoc, extracted, traceId)                                │
│                                                                              │
│ 1. buildChildDocument()                                                     │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ childDoc = ProductDocumentToProcess.builder()                     │    │
│    │     .documentId(zipDoc.documentId + "_" + extracted.filename)      │    │
│    │     .productId(zipDoc.productId)                                   │    │
│    │     .parentDocumentId(zipDoc.documentId)  ← Link al padre        │    │
│    │     .filename(extracted.filename)                                  │    │
│    │     .content(extracted.content)                                    │    │
│    │     .contentType(extracted.contentType)                            │    │
│    │     .origin(zipDoc.origin)                                         │    │
│    │     .status(PENDING)                                               │    │
│    │     .isZipArchive(false)                                           │    │
│    └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│ 2. documentRepository.save(childDoc)                                        │
│ 3. processDocumentInternal(childDoc, traceId)  ← Procesar hijo              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 3.1.3 aggregateZipResults() - Agregar Resultados

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ aggregateZipResults(zipDoc, results, traceId)                              │
│                                                                              │
│ 1. Calcular resultado global                                                 │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ allSuccess = results.stream().allMatch(FileUploadResult::isSuccess) │  │
│    └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│ 2. Determinar status del ZIP padre                                          │
│    - allSuccess = true  → status = SUCCESS                                │
│    - allSuccess = false → status = FAILURE                                │
│                                                                              │
│ 3. UPDATE documento ZIP padre                                                │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ UPDATE product_documents_to_process                                 │    │
│    │ SET status = parentStatus,                                         │    │
│    │     error_code = (allSuccess ? null : ZIP_PARTIAL_FAILURE)        │    │
│    │ WHERE document_id = zipDoc.documentId                               │    │
│    └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│ 4. RETURN FileUploadResult                                                   │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ FileUploadResult.builder()                                         │    │
│    │     .status(parentStatus)                                          │    │
│    │     .correlationId(zipDoc.documentId)                              │    │
│    │     .success(allSuccess)                                           │    │
│    │     .errorCode(allSuccess ? null : ZIP_PARTIAL_FAILURE)           │    │
│    │     .message("ZIP processed [successfully|with failures] " +       │    │
│    │              "with " + results.size() + " documents")            │    │
│    └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Casos de Error en Procesamiento ZIP

| Caso | Causa | Resultado |
|------|-------|-----------|
| ZIP corrupto | `ZipInputStream` lanza exception | Documento ZIP → `FAILURE` (ZIP_EXTRACTION_FAILED) |
| ZIP vacio | `children.isEmpty()` | Documento ZIP → `SUCCESS` con mensaje "ZIP was empty" |
| Hijo falla | `processDocumentInternal()` fails | ZIP → `FAILURE` (ZIP_PARTIAL_FAILURE), hijo individual tiene su estado |
| Todos los hijos success | `allSuccess = true` | ZIP → `SUCCESS` |
| Algún hijo falla | `allSuccess = false` | ZIP → `FAILURE` (ZIP_PARTIAL_FAILURE) |

---

## Escenarios de Procesamiento de Documentos

### 1. Escenario: Procesamiento Exitoso

**Condicion:** Todos los documentos se procesan sin errores.

```
Flujo:
1. claimDocument() → rowsUpdated=1 → documento clonado
2. content disponible (pre-cargado o baixado)
3. Validaciones passent (tamano, tipo, origin)
4. sendWithResilience() → HTTP 200 + SOAPResponse.success
5. checkpoint(SUCCESS) → UPDATE documento
6. postProcess() → log final

Resultado:
- documento.status = SUCCESS
- documento.correlation_id = ID del servicio
- documento.processed_at = timestamp
- producto.status = recalculado
```

### 2. Escenario: Fallo Permanente (No Retry)

**Condicion:** Error que no permite reintento (ej. archivo corrupto, validation failed).

```
Flujo:
1. claimDocument() → rowsUpdated=1
2. Validacion falla → FileValidationException
3. handleFailure() → status=FAILURE, errorCode=VALIDATION_FAILED
4. checkpoint(FAILURE) → UPDATE documento

Resultado:
- documento.status = FAILURE
- documento.error_code = VALIDATION_FAILED
- documento.processed_at = timestamp
- No se ejecuta send()
```

### 3. Escenario: Fallo Transitorio (Retry)

**Condicion:** Error de red o servicio no disponible.

```
Flujo:
1. claimDocument() → rowsUpdated=1
2. send() → SoapCommunicationException (timeout, 503, etc.)
3. isRetryableException() → true
4. Retry.backoff() → reintentos con exponential backoff
5. Si todos fallan → handleFailure()
6. checkpoint(FAILURE) → status=RETRY

Resultado:
- documento.status = RETRY
- documento.error_code = GATEWAY_TIMEOUT | SERVICE_UNAVAILABLE
- documento.retry_count = 3
```

### 4. Escenario: Producto con Documentos Mixtos

**Condicion:** Algunos documentos success, otros failure.

```
Flujo:
1. findPendingDocuments() → Flux de 5 documentos
2. doc1 → SUCCESS, doc2 → SUCCESS, doc3 → FAILURE
3. doc4 → SUCCESS, doc5 → FAILURE
4. Cuando todos los docs de un producto estan processed:
   → ProductStatusAggregator.recalculate()
   → producto.status = PARTIAL_FAILURE

Resultado:
- 3 docs SUCCESS, 2 docs FAILURE
- producto.status = PARTIAL_FAILURE
```

### 5. Escenario: ZIP Vacio

**Condicion:** El ZIP no contiene archivos hijos.

```
Flujo:
1. extractZipChildren() → lista vacia
2. handleEmptyZip()
3. UPDATE documento ZIP → status=SUCCESS, message="ZIP was empty"
4. No se crean documentos hijos

Resultado:
- documento ZIP.status = SUCCESS
- documento ZIP.message = "ZIP was empty"
- No se crean child documents
```

### 6. Escenario: ZIP con Archivos Mixtos

**Condicion:** ZIP contiene archivos validos e invalidos.

```
Flujo:
1. extractZipChildren() → 10 archivos extraidos
2. processZipChildren() → flatMapSequential
3. child1-5 → SUCCESS, child6 → FAILURE (tipo no permitido)
4. child7-10 → SUCCESS
5. aggregateZipResults() → allSuccess=false
6. UPDATE ZIP padre → status=FAILURE, errorCode=ZIP_PARTIAL_FAILURE

Resultado:
- ZIP padre.status = FAILURE
- ZIP padre.error_code = ZIP_PARTIAL_FAILURE
- children tienen sus propios estados (SUCCESS/FAILURE)
```

### 7. Escenario: Claim atomico ( concurrency)

**Condicion:** Multiple pods procesan el mismo documento.

```
Flujo:
1. Pod A y Pod B llaman claimDocument(documentId)
2. Database: UPDATE ... WHERE document_id=$1 AND status IN ('PENDING','RETRY')
3. Pod A → rowsUpdated=1 → continua
4. Pod B → rowsUpdated=0 → skip

Resultado:
- Solo 1 pod procesa el documento
- El otro pod hace skip y continua con el siguiente
```

### 8. Escenario: Documento no encontrado en REST API

**Condicion:** El documento tiene `content=null` y no existe en REST API.

```
Flujo:
1. downloadContentIfNeeded() → GET /products/{id}/docs/{id}
2. REST API returns 404
3. handleFailure() → status=FAILURE, errorCode=DOCUMENT_NOT_FOUND

Resultado:
- documento.status = FAILURE
- documento.error_code = DOCUMENT_NOT_FOUND
```

---

## Flujos de Persistencia de Datos

### Carga de Productos (LoadProductsUseCase)

```
OBTENER (GET) → REST API Externa
│
├── GET /products → List<ProductInfo>
│
├── Por cada producto:
│   ├── INSERT products_to_process
│   │   └── status = PENDING
│   │
│   └── Por cada documento en producto:
│       ├── Validar: es ZIP? → ZipArchive.extractDocuments()
│       ├── INSERT product_documents_to_process ( padre o hijos )
│       └── content = Base64.decode( base64Content )
│
└── RETURN LoadProductsResult
```

**Guardar:**
- `products_to_process`: product_id, name, status=PENDING, trace_id, created_at
- `product_documents_to_process`: document_id, product_id, filename, content, content_type, origin, status=PENDING

### Procesamiento de Documentos (AbstractDocumentProcessingUseCase)

```
OBTENER (SELECT) → Base de Datos
│
├── findPendingDocuments()
│   └── SELECT * FROM product_documents_to_process
│       WHERE status IN ('PENDING', 'RETRY')
│       ORDER BY created_at ASC
│
├── Por cada documento:
│   ├── claimDocument() → UPDATE ... RETURNING *
│   │
│   ├── MODIFICAR (UPDATE) → claim atomico
│   │   └── status = PROCESSING (solo si rowsUpdated > 0)
│   │
│   ├── Si content null:
│   │   ├── OBTENER (GET) → REST API
│   │   │   └── GET /products/{productId}/documents/{documentId}
│   │   └── MODIFICAR (UPDATE) → content
│   │
│   ├── Si es ZIP:
│   │   ├── MODIFICAR (INSERT) → hijos extraidos
│   │   └── processZipChildren() →递归
│   │
│   ├── ENVIAR (POST) → SOAP o S3
│   │
│   └── MODIFICAR (UPDATE) → resultado final
│       └── status, correlation_id, processed_at, latency_ms, error_code
│
└── Verificar todos los docs del producto:
    ├── OBTENER → count docs por status
    └── MODIFICAR (UPDATE) → product status
```

### Calculo de Estado de Producto (ProductStatusAggregator)

```
OBTENER (SELECT) → product_documents_to_process
│
├── findDocumentsByProduct(productId)
│   └── SELECT status, COUNT(*) FROM product_documents_to_process
│       WHERE product_id = $1 GROUP BY status
│
├── Calcular:
│   ├── allSuccess = todos status=SUCCESS
│   ├── anyFailure = existe status=FAILURE
│   ├── hasPending = existe status=PENDING/PROCESSING/RETRY
│   ├── allSkipped = todos status=SKIPPED/NOT_SENT
│   │
│   └── Reglas:
│       ├── hasPending → PENDING
│       ├── allSuccess → SUCCESS
│       ├── anyFailure && !allFailure → PARTIAL_FAILURE
│       ├── allSkipped → COMPLETED_WITH_SKIPS
│       └── allFailure → COMPLETED_WITH_FAILURES
│
└── MODIFICAR (UPDATE) → products_to_process.status
```

---

## Reglas de Validacion y Restricciones

### Validacion de Archivos

| Regla | soap | s3 | Descripcion |
|-------|------|----|-------------|
| Tamano maximo | 10MB | 50MB | `max-size` en configuracion |
| Tipos permitidos | pdf,txt,csv | pdf,txt,csv,zip | `allowed-types` |
| Longitud filename | 255 chars | 255 chars | `max-filename-length` |
| Origen valido | incoming,documents | incoming,documents | `origin-patterns-to-send` |

### Patrones de Origin (Regex)

```
SOAP:
- origin-patterns-to-send: "incoming", "documents"
- folder-exclusion-regex: ".*/temp/.*", ".*/backup/.*"

S3:
- origin-patterns-to-send: "incoming", "documents", "exports"
- folder-exclusion-regex: ".*/temp/.*"
```

### Formatos de Content-Type Soportados

| Content-Type | Extension | soap | s3 |
|--------------|-----------|------|-----|
| application/pdf | .pdf | ✅ | ✅ |
| text/plain | .txt | ✅ | ✅ |
| text/csv | .csv | ✅ | ✅ |
| application/zip | .zip | ❌ | ✅ |
| application/json | .json | ❌ | ✅ |
| image/png | .png | ❌ | ✅ |

### Codigos de Error

| Codigo | Descripcion | Retry |
|--------|-------------|-------|
| `VALIDATION_FAILED` | Archivo no cumple validaciones | No |
| `INVALID_RESPONSE` | Respuesta SOAP invalida | No |
| `DOCUMENT_NOT_FOUND` | Documento no existe en REST API | No |
| `ZIP_EXTRACTION_FAILED` | Error extrayendo ZIP | No |
| `ZIP_PARTIAL_FAILURE` | Algunos hijos fallaron | No |
| `GATEWAY_TIMEOUT` | Timeout en gateway | Si |
| `SERVICE_UNAVAILABLE` | Servicio no disponible (503) | Si |
| `BAD_GATEWAY` | Error 500 del servicio | Si |
| `CLIENT_ERROR` | Error 4xx del servicio | No |
| `UNKNOWN_ERROR` | Error no categorizado | No |

### Estados de Documento y Transiciones

```
PENDING → PROCESSING (claim exitoso)
PENDING → NOT_SENT (validacion de origin/tamano/tipo falla)
PROCESSING → SUCCESS (envio exitoso)
PROCESSING → RETRY (error transitorio, reintentos disponibles)
PROCESSING → FAILURE (error permanente o reintentos agotados)
RETRY → PROCESSING (proximo intento)
RETRY → FAILURE (reintentos agotados)
```

### Latencia Esperada por Gateway

| Gateway | Latencia P50 | Latencia P99 | Timeout |
|---------|--------------|--------------|---------|
| SOAP | 150ms | 500ms | 30s |
| S3 | 80ms | 200ms | 30s |

---

## Modelo de Datos

### Tablas

**products_to_process**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| product_id | VARCHAR(255) | PK |
| name | VARCHAR(500) | Nombre del producto |
| status | VARCHAR(50) | Estado del producto |
| trace_id | VARCHAR(255) | UUID de traza |
| created_at | TIMESTAMP | Fecha de creacion |
| processed_at | TIMESTAMP | Fecha de procesamiento |

**product_documents_to_process**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| document_id | VARCHAR(255) | PK |
| product_id | VARCHAR(255) | FK a products_to_process |
| parent_document_id | VARCHAR(255) | PK del ZIP padre (null si no es hijo) |
| filename | VARCHAR(255) | Nombre del archivo |
| content | TEXT | Contenido (Base64 encoded) |
| content_type | VARCHAR(255) | Tipo MIME |
| origin | VARCHAR(500) | Origen (ej. folderA/incoming) |
| status | VARCHAR(50) | PENDING, PROCESSING, SUCCESS, FAILURE, RETRY, SKIPPED, NOT_SENT |
| created_at | TIMESTAMP | Fecha de creacion |
| processed_at | TIMESTAMP | Fecha de procesamiento |
| trace_id | VARCHAR(255) | UUID de traza |
| correlation_id | VARCHAR(255) | ID del servicio externo (SOAP correlationId o S3 eTag) |
| error_code | VARCHAR(100) | Codigo de error |
| latency_ms | BIGINT | Latencia del gateway (ms) |
| gateway_name | VARCHAR(50) | Nombre del gateway (SOAP/S3) |
| metadata | VARCHAR(1000) | Metadatos adicionales (JSON) |

**communication_log**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| trace_id | VARCHAR(255) | UUID de traza |
| document_id | VARCHAR(255) | Documento asociado |
| status | VARCHAR(50) | SUCCESS/FAILURE |
| retry_count | INT | Numero de reintentos |
| error_code | VARCHAR(100) | Codigo de error |
| filename | VARCHAR(255) | Nombre del archivo |
| created_at | TIMESTAMP | Fecha del log |
| latency_ms | BIGINT | Latencia (ms) |
| gateway_name | VARCHAR(50) | SOAP/S3 |
| metadata | VARCHAR(1000) | JSON metadata |

---

## API Endpoints

### GET /api/v1/products/load

Carga productos desde REST API externa. Los ZIP se expanden automaticamente.

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion

**Response:**
```json
{
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "operationType": "LOAD",
  "status": "LOADING",
  "message": "Product loading from REST API started",
  "success": true
}
```

### GET /api/v1/products?processor={soap|s3}

Procesa documentos pendientes.

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion

**Parametros:**
- `processor`: `soap` (default) | `s3`

**Response:**
```json
{
  "traceId": "660e8400-e29b-41d4-a716-446655440001",
  "operationType": "PROCESS",
  "status": "PROCESSING",
  "message": "Pending product documents processing started",
  "success": true
}
```

---

## Estados de Documento

| Estado | Descripcion |
|--------|-------------|
| `PENDING` | Esperando procesamiento |
| `PROCESSING` | Siendo procesado (claimado) |
| `SUCCESS` | Enviado exitosamente |
| `FAILURE` | Error permanente |
| `RETRY` | Error reintentable |
| `SKIPPED` | Excluido por carpeta |
| `NOT_SENT` | No enviado (tamano, tipo, u origin no valido) |

---

## Estados de Producto

Calculados automaticamente por `ProductStatusAggregator`:

| Estado | Condicion |
|--------|-----------|
| `PENDING` | Al menos 1 doc PENDING/PROCESSING/RETRY |
| `SUCCESS` | Todos los docs SUCCESS |
| `PARTIAL_FAILURE` | Al menos 1 FAILURE (pero no todos) |
| `COMPLETED_WITH_SKIPS` | Todos SUCCESS/SKIPPED |
| `COMPLETED_WITH_NOT_SENT` | Todos SUCCESS/SKIPPED/NOT_SENT |
| `COMPLETED_WITH_FAILURES` | Todos FAILURE |

---

## Template Method Pattern

```
AbstractDocumentProcessingUseCase
├── executePendingDocuments()          ← FINAL
└── processDocumentInternal(doc, id)  ← FINAL (template)
     ├── 1. prepareDocument()         ← ABSTRACT (subclase)
     ├── 2. buildRequest()             ← CONCRETO
     ├── 3. sendWithResilience()       ← CONCRETO
     ├── 4. checkpoint()               ← CONCRETO
     └── 5. postProcess()              ← CONCRETO

SoapDocumentProcessingUseCase     S3DocumentProcessingUseCase
  - prepareDocument()                - prepareDocument()
  - implementationName()             - implementationName()
```

---

## Configuracion

### Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Servicio SOAP |
| `AWS_ENDPOINT` | `` | Endpoint S3 (LocalStack) |
| `AWS_BUCKET` | `documents-bucket` | Bucket S3 |
| `AWS_REGION` | `us-east-1` | Region AWS |
| `AWS_ACCESS_KEY` | `` | Access key |
| `AWS_SECRET_KEY` | `` | Secret key |

### Configuracion de Procesadores

```yaml
app:
  processors:
    soap:
      max-size: 10485760          # 10MB
      allowed-types: pdf,txt,csv
      max-filename-length: 255
      origin-patterns-to-send: incoming,documents
      folder-exclusion-regex:      # Patrones regex de exclusion
        - .*/temp/.*
        - .*/backup/.*
    s3:
      max-size: 52428800         # 50MB
      allowed-types: pdf,txt,csv,zip
      retry-attempts: 3
      retry-backoff-millis: 500
```

---

## Seguridad

### Proteccion contra ZIP Bombs

`ZipArchive.extractDocuments()` implementa limites:

- `maxEntries`: 1000 entries maximas por ZIP
- `maxUncompressedSize`: 100MB tamano maximo descomprimido total

### Sanitizacion de S3 Keys

`S3GatewayAdapter.buildKey()` sanitiza el filename:
- Elimina `..` (path traversal)
- Elimina `/` y `\` (path separators)
- Previene escribir fuera del bucket

### SQL Injection

Queries parametrizados via `DatabaseClient.bind()`:
```java
// CORRECTO
databaseClient.sql("SELECT ... WHERE id = $1").bind("$1", id)

// INCORRECTO (eliminado)
databaseClient.sql("SELECT ... WHERE id = '" + id + "'")
```

---

## Compilacion y Ejecucion

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Ejecutar con mock REST
DOCUMENT_REST_ENDPOINT=http://localhost:3001 ./gradlew bootRun

# Ejecutar con S3 mock
AWS_ENDPOINT=http://localhost:4566 ./gradlew bootRun -Ps3
```

---

## Ejemplos de curl

```bash
# Cargar productos
curl -X GET http://localhost:8080/api/v1/products/load \
  -H "message-id: my-trace-123"

# Procesar con SOAP
curl -X GET "http://localhost:8080/api/v1/products?processor=soap" \
  -H "message-id: my-trace-123"

# Procesar con S3
curl -X GET "http://localhost:8080/api/v1/products?processor=s3" \
  -H "message-id: my-trace-123"

# Health check
curl -s http://localhost:8080/actuator/health
```
