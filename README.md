# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envía a un servicio SOAP externo o AWS S3.

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas claras:

```
com.example.fileprocessor/
├── domain/                          # Capa de dominio (puro Java, sin frameworks)
│   ├── entity/                      # Entidades de negocio
│   │   ├── ProductToProcess.java           # Producto en BD
│   │   ├── ProductDocumentToProcess.java   # Documento de producto en BD
│   │   ├── ProductInfo.java                # Producto desde REST API
│   │   ├── ProductDocumentInfo.java       # Documento dentro de ProductInfo
│   │   ├── FileUploadResult.java          # Resultado de upload/procesamiento
│   │   ├── ExternalServiceResponse.java   # Respuesta genérica de servicio externo
│   │   ├── ZipArchive.java                # ZIP con protección contra bombs
│   │   ├── AsyncOperationStatus.java     # Estado de operación async
│   │   ├── DocumentStatus.java           # Estados de documento (constantes)
│   │   └── ProductStatus.java            # Estados de producto
│   ├── usecase/                      # Casos de uso
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base
│   │   ├── SoapDocumentProcessingUseCase.java     # Implementación SOAP
│   │   ├── S3DocumentProcessingUseCase.java       # Implementación S3
│   │   ├── LoadProductsUseCase.java               # Carga productos y documentos
│   │   ├── LoadProductsResult.java                # Resultado de carga
│   │   ├── FileValidator.java                     # Validador de archivos
│   │   ├── ZipProcessor.java                      # Procesador de ZIP
│   │   ├── DocumentToUpload.java                 # Record con info de documento
│   │   ├── ProductStatusAggregator.java          # Agregación de estado
│   │   ├── ProductStatusSummary.java             # Resumen de estado
│   │   └── ProcessingResultCodes.java            # Códigos de resultado
│   ├── util/
│   │   ├── Base64Utils.java                # Encoding/decoding Base64
│   │   └── MediaTypeConstants.java         # Constantes de tipos MIME
│   ├── valueobject/
│   │   └── FolderExclusionRegexConfig.java  # Patrones regex (fail-fast)
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java    # Configuración de validación
│   │   └── out/
│   │       ├── ProductRestGateway.java     # Puerto REST productos
│   │       ├── ProductRepository.java      # Puerto productos
│   │       ├── ProductDocumentRepository.java  # Puerto documentos
│   │       ├── S3Gateway.java              # Puerto S3
│   │       └── SoapGateway.java           # Puerto SOAP
│   └── exception/
│       ├── DomainException.java
│       ├── FileValidationException.java
│       ├── ProcessingException.java       # Excepción unificada de procesamiento
│       └── InvalidBase64Exception.java    # Error en decoding Base64
│
├── application/                      # Configuración de aplicación
│   └── app-service/
│       └── config/
│           ├── DomainConfig.java         # Beans de casos de uso
│           └── DatabaseInitializer.java  # Inicialización de BD
│
└── infrastructure/                  # Capa de infraestructura
    ├── entrypoints/
    │   └── rest/
    │       ├── ProductRoutes.java        # RouterFunction
    │       ├── handler/
    │       │   └── ProductHandler.java   # Lógica de handlers
    │       ├── config/
    │       │   └── DocumentRestProperties.java
    │       └── constants/
    │           ├── RestApiPaths.java
    │           └── ApiConstants.java     # Constantes API (message-id, processor types)
    ├── drivenadapters/
    │   ├── rest-client/
    │   │   └── ProductRestGatewayAdapter.java
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java
    │   │   └── config/
    │   │       └── SoapProperties.java
    │   ├── aws/
    │   │   ├── S3GatewayAdapter.java
    │   │   └── config/
    │   │       ├── AwsConfig.java
    │   │       └── S3Properties.java
    │   └── r2dbc/
    │       ├── R2dbcProductRepository.java
    │       └── R2dbcProductDocumentRepository.java
    └── helpers/
        ├── config/
        │   ├── ProcessorConfig.java      # Config unificada procesadores
        │   └── ProcessorSettings.java    # Settings de procesador
        └── soap/
            ├── SoapConstants.java        # Constantes SOAP (namespaces, envelopes)
            ├── mapper/
            │   └── SoapMapper.java       # Mapeo XML ↔ objetos
            ├── xml/
            │   ├── SoapEnvelopeWrapper.java  # Wrapper de envelope SOAP
            │   └── model/
            │       ├── UploadFileRequest.java
            │       └── UploadFileResponse.java
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
│                           API Request                                         │
│                      GET /api/v1/products?processor=soap                    │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductHandler.processPendingProducts()                                  │
│    - Valida processor type (soap|s3)                                         │
│    - Genera traceId UUID si no existe en header                             │
│    - Retorna HTTP 202 ACCEPTED inmediatamente                                │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 2. AbstractDocumentProcessingUseCase.executePendingDocuments()             │
│    - findPendingDocuments() → Flux<ProductDocumentToProcess>               │
│    - flatMap con validateMetadataDocument → retrieveDocument → uploadDocument│
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 3. validateMetadataDocument() - Claim Atómico                                 │
│                                                                              │
│    claimDocument(documentId)                                                 │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ UPDATE product_documents_to_process                                  │    │
│    │ SET status = 'PROCESSING'                                           │    │
│    │ WHERE document_id = $1 AND status IN ('PENDING', 'RETRY')           │    │
│    └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│    Si rowsUpdated > 0 → documento clonado, continuar                        │
│    Si rowsUpdated = 0 → otro pod lo tomó, skip                              │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
         ┌──────────────────┐           ┌──────────────────────────────┐
         │ Documento YA    │           │ Documento NECESITA          │
         │ tiene content   │           │ downloadContentIfNeeded()   │
         │ (pre-cargado)   │           │ - GET /products/{id}/docs/{id}│
         └────────┬─────────┘           │ - Decode Base64             │
                  │                     └──────────────┬───────────────┘
                  │                                    │
                  └──────────────┬──────────────────────┘
                                 ▼
                   ┌─────────────────────────────┐
                   │ isZipArchive?               │
                   ├─────────────────────────────┤
                   │ true  → processZipDocument │
                   │ false → applyRulesMetadata│
                   └─────────────────────────────┘
```

### Pipeline de Procesamiento

El pipeline usa **Reactor** (Project Reactor) con operaciones reactivas:

```
findPendingDocuments()
    │
    ▼
validateMetadataDocument(doc)     ← Claim + Validación
    │                              (extensión, tamaño, folder exclusion)
    ▼
retrieveDocument(doc)            ← Download content si null
    │
    ▼
uploadDocument(doc)              ← SOAP o S3
```

### Process ZIP Document (Desglose)

Cuando un documento es ZIP, se extrae y procesa cada hijo:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ processZipDocument(zipDoc)                                                   │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 3.1 extractAndValidate()                                                   │
│                                                                              │
│    ZipArchive.builder()                                                      │
│        .zipContent(bytes)                                                   │
│        .originalFilename(name)                                               │
│        .maxEntries(1000)           ← Protección contra ZIP bombs           │
│        .maxUncompressedSize(100MB)                                           │
│        .build()                                                              │
│                                                                              │
│    archive.extractDocuments()                                                │
│    ┌────────────────────────────────────────────────────────────────────┐    │
│    │ Validaciones durante extracción:                                  │    │
│    │ - Contador de entries <= maxEntries (1000)                       │    │
│    │ - Tamaño descomprimido por entry <= maxUncompressedSize           │    │
│    │ - Tamaño total acumulado <= maxUncompressedSize                   │    │
│    │                                                                      │    │
│    │ Si falla: FileValidationException ZIP_EXTRACTION_FAILED          │    │
│    └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│    Casos:                                                                   │
│    - ZIP válido con hijos → retorna lista de ExtractedDocument               │
│    - ZIP vacío → retorna lista vacía []                                    │
│    - ZIP corrupto/inválido → exception → status=FAILURE                    │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
         ┌──────────────────┐           ┌──────────────────────────────┐
         │ children.isEmpty │           │ children.isNotEmpty         │
         │ (ZIP vacío)       │           │ saveAll(children)           │
         └────────┬──────────┘           │ processZipChildren()        │
                  │                      └──────────────┬───────────────┘
                  ▼                                     ▼
         UPDATE ZIP → SUCCESS                 ┌─────────────────────────┐
         (status=SUCCESS)                      │ ZIP padre actualizado  │
                                                └─────────────────────────┘
```

### Casos de Error en Procesamiento ZIP

| Caso | Causa | Resultado |
|------|-------|-----------|
| ZIP corrupto | `ZipInputStream` lanza exception | Documento ZIP → `FAILURE` (ZIP_EXTRACTION_FAILED) |
| ZIP vacío | `children.isEmpty()` | Documento ZIP → `SUCCESS` con mensaje "ZIP was empty" |
| Hijo falla | `processZipChildren()` fails | ZIP → `FAILURE` (ZIP_PARTIAL_FAILURE), hijo individual tiene su estado |
| Todos los hijos success | `allSuccess = true` | ZIP → `SUCCESS` |
| Algún hijo falla | `allSuccess = false` | ZIP → `FAILURE` (ZIP_PARTIAL_FAILURE) |

---

## Escenarios de Procesamiento de Documentos

### 1. Escenario: Procesamiento Exitoso

**Condición:** Todos los documentos se procesan sin errores.

```
Flujo:
1. claimDocument() → rowsUpdated=1 → documento clonado
2. content disponible (pre-cargado o baixado)
3. Validaciones passent (tamaño, tipo, origin)
4. uploadDocument() → HTTP 200 + ExternalServiceResponse.success
5. UPDATE documento → SUCCESS
6. ProductStatusAggregator recalcula estado del producto

Resultado:
- documento.status = SUCCESS
- documento.correlation_id = ID del servicio
- documento.processed_at = timestamp
- producto.status = recalculado
```

### 2. Escenario: Fallo Permanente (No Retry)

**Condición:** Error que no permite reintento (ej. archivo corrupto, validación failed).

```
Flujo:
1. claimDocument() → rowsUpdated=1
2. Validación falla → FileValidationException
3. onErrorResume() → status=FAILURE, errorCode=VALIDATION_FAILED
4. checkpoint(FAILURE) → UPDATE documento

Resultado:
- documento.status = FAILURE
- documento.error_code = VALIDATION_FAILED
- documento.processed_at = timestamp
- No se ejecuta uploadDocument()
```

### 3. Escenario: Fallo Transitorio (Retry)

**Condición:** Error de red o servicio no disponible.

```
Flujo:
1. claimDocument() → rowsUpdated=1
2. uploadDocument() → ProcessingException (timeout, 503, etc.)
3. isRetryableException() → true
4. Retry.backoff() → reintentos con exponential backoff
5. Si todos fallan → onErrorResume() → FAILURE
6. checkpoint(FAILURE) → UPDATE documento

Resultado:
- documento.status = FAILURE
- documento.error_code = GATEWAY_TIMEOUT | BAD_GATEWAY
```

### 4. Escenario: Producto con Documentos Mixtos

**Condición:** Algunos documentos success, otros failure.

```
Flujo:
1. findPendingDocuments() → Flux de 5 documentos
2. doc1 → SUCCESS, doc2 → SUCCESS, doc3 → FAILURE
3. doc4 → SUCCESS, doc5 → FAILURE
4. Cuando todos los docs de un producto están processed:
   → ProductStatusAggregator.recalculate()
   → producto.status = PARTIAL_FAILURE

Resultado:
- 3 docs SUCCESS, 2 docs FAILURE
- producto.status = PARTIAL_FAILURE
```

### 5. Escenario: ZIP Vacío

**Condición:** El ZIP no contiene archivos hijos.

```
Flujo:
1. extractAndValidate() → lista vacía
2. UPDATE documento ZIP → status=SUCCESS
3. No se crean documentos hijos

Resultado:
- documento ZIP.status = SUCCESS
- No se crean child documents
```

### 6. Escenario: ZIP con Archivos Mixtos

**Condición:** ZIP contiene archivos válidos e inválidos.

```
Flujo:
1. extractAndValidate() → 10 archivos extraídos
2. saveAll() → persistir hijos en BD
3. processZipChildren() → flatMapSequential
4. child1-5 → SUCCESS, child6 → FAILURE (tipo no permitido)
5. child7-10 → SUCCESS
6. aggregateZipResults() → allSuccess=false
7. UPDATE ZIP padre → status=FAILURE, errorCode=ZIP_PARTIAL_FAILURE

Resultado:
- ZIP padre.status = FAILURE
- ZIP padre.error_code = ZIP_PARTIAL_FAILURE
- children tienen sus propios estados (SUCCESS/FAILURE)
```

### 7. Escenario: Claim Atómico (Concurrency)

**Condición:** Múltiples pods procesan el mismo documento.

```
Flujo:
1. Pod A y Pod B llaman claimDocument(documentId)
2. Database: UPDATE ... WHERE document_id=$1 AND status IN ('PENDING','RETRY')
3. Pod A → rowsUpdated=1 → continua
4. Pod B → rowsUpdated=0 → skip

Resultado:
- Solo 1 pod procesa el documento
- El otro pod hace skip y continúa con el siguiente
```

### 8. Escenario: Documento no encontrado en REST API

**Condición:** El documento tiene `content=null` y no existe en REST API.

```
Flujo:
1. retrieveDocument() → GET /products/{id}/docs/{id}
2. REST API returns 404
3. onErrorResume() → status=FAILURE, errorCode=DOCUMENT_NOT_FOUND

Resultado:
- documento.status = FAILURE
- documento.error_code = DOCUMENT_NOT_FOUND
```

---

## Flujos de Persistencia de Datos

### Carga de Productos (LoadProductsUseCase)

```
REST API Externa
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
Base de Datos
│
├── findPendingDocuments()
│   └── SELECT * FROM product_documents_to_process
│       WHERE status IN ('PENDING', 'RETRY')
│       ORDER BY created_at ASC
│
├── Por cada documento:
│   ├── claimDocument() → UPDATE ... RETURNING *
│   │
│   ├── Si content null:
│   │   ├── GET → REST API
│   │   │   └── GET /products/{productId}/documents/{documentId}
│   │   └── UPDATE → content
│   │
│   ├── applyRulesMetadata() → Validación (extensión, tamaño)
│   │
│   ├── Si es ZIP:
│   │   ├── extractAndValidate() → extraer hijos
│   │   ├── saveAll() → persistir hijos
│   │   └── processZipChildren() → procesar recursivamente
│   │
│   ├── uploadDocument() → SOAP o S3
│   │
│   └── UPDATE → resultado final
│       └── status, correlation_id, processed_at, error_code
│
└── Verificar todos los docs del producto:
    ├── SELECT → count docs por status
    └── UPDATE → product status
```

### Cálculo de Estado de Producto (ProductStatusAggregator)

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
└── UPDATE → products_to_process.status
```

---

## Reglas de Validación y Restricciones

### Validación de Archivos (FileValidator)

| Regla | soap | s3 | Descripción |
|-------|------|----|-------------|
| Tamaño máximo | 10MB | 50MB | `max-size` en configuración |
| Tipos permitidos | pdf,txt,csv | pdf,txt,csv,zip | `allowed-types` |
| Longitud filename | 255 chars | 255 chars | Validación en FileValidator |
| Folder exclusion | regex | regex | Patrones configurables |

### Formatos de Content-Type Soportados

| Content-Type | Extensión | soap | s3 |
|--------------|-----------|------|-----|
| application/pdf | .pdf | ✅ | ✅ |
| text/plain | .txt | ✅ | ✅ |
| text/csv | .csv | ✅ | ✅ |
| application/zip | .zip | ❌ | ✅ |
| application/json | .json | ❌ | ✅ |
| image/png | .png | ❌ | ✅ |

### Códigos de Error (ProcessingResultCodes)

| Código | Descripción | Retry |
|--------|-------------|-------|
| `FILE_SIZE_EXCEEDED` | Archivo excede tamaño máximo | No |
| `INVALID_FILE_TYPE` | Extensión no permitida | No |
| `FILENAME_TOO_LONG` | Nombre de archivo muy largo | No |
| `INVALID_FILENAME` | Nombre de archivo inválido | No |
| `ZIP_EXTRACTION_FAILED` | Error extrayendo ZIP | No |
| `ZIP_PARTIAL_FAILURE` | Algunos hijos fallaron | No |
| `GATEWAY_TIMEOUT` | Timeout en gateway | Si |
| `BAD_GATEWAY` | Error 500 del servicio | Si |
| `CLIENT_ERROR` | Error 4xx del servicio | No |
| `UNKNOWN_ERROR` | Error no categorizado | No |
| `EMPTY_CONTENT` | Content vacío (Base64) | No |
| `INVALID_BASE64` | Base64 inválido | No |

### Estados de Documento (DocumentStatus)

```
PENDING → PROCESSING (claim exitoso)
PENDING → SKIPPED (folder exclusion match)
PROCESSING → SUCCESS (envío exitoso)
PROCESSING → FAILURE (error permanente)
RETRY → PROCESSING (próximo intento)
RETRY → FAILURE (reintentos agotados)
```

### Estados de Producto (ProductStatus)

Calculados automáticamente por `ProductStatusAggregator`:

| Estado | Condición |
|--------|-----------|
| `PENDING` | Al menos 1 doc PENDING/PROCESSING/RETRY |
| `SUCCESS` | Todos los docs SUCCESS |
| `PARTIAL_FAILURE` | Al menos 1 FAILURE (pero no todos) |
| `COMPLETED_WITH_SKIPS` | Todos SUCCESS/SKIPPED |
| `COMPLETED_WITH_NOT_SENT` | Todos SUCCESS/SKIPPED/NOT_SENT |
| `COMPLETED_WITH_FAILURES` | Todos FAILURE |

---

## Modelo de Datos

### Tablas

**products_to_process**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| product_id | VARCHAR(255) | PK |
| name | VARCHAR(500) | Nombre del producto |
| status | VARCHAR(50) | Estado del producto |
| trace_id | VARCHAR(255) | UUID de traza |
| created_at | TIMESTAMP | Fecha de creación |
| processed_at | TIMESTAMP | Fecha de procesamiento |

**product_documents_to_process**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| document_id | VARCHAR(255) | PK |
| product_id | VARCHAR(255) | FK a products_to_process |
| parent_document_id | VARCHAR(255) | PK del ZIP padre (null si no es hijo) |
| filename | VARCHAR(255) | Nombre del archivo |
| content | TEXT | Contenido (Base64 encoded) |
| content_type | VARCHAR(255) | Tipo MIME |
| origin | VARCHAR(500) | Origen (ej. folderA/incoming) |
| status | VARCHAR(50) | PENDING, PROCESSING, SUCCESS, FAILURE, RETRY, SKIPPED |
| is_zip_archive | BOOLEAN | Indica si es ZIP |
| created_at | TIMESTAMP | Fecha de creación |
| processed_at | TIMESTAMP | Fecha de procesamiento |
| correlation_id | VARCHAR(255) | ID del servicio externo |
| error_code | VARCHAR(100) | Código de error |
| latency_ms | BIGINT | Latencia del gateway (ms) |
| gateway_name | VARCHAR(50) | Nombre del gateway (SOAP/S3) |

---

## API Endpoints

### GET /api/v1/products/load

Carga productos desde REST API externa. Los ZIP se expanden automáticamente.

**Headers:**
- `message-id`: (opcional) Trace ID para correlación

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
- `message-id`: (opcional) Trace ID para correlación

**Parámetros:**
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

### GET /actuator/health

Health check de la aplicación.

**Response:**
```json
{
  "status": "UP"
}
```

---

## Template Method Pattern

```
AbstractDocumentProcessingUseCase
│
├── executePendingDocuments()          ← FINAL (template method)
│   │
│   ├── findPendingDocuments()
│   ├── validateMetadataDocument()      ← FINAL (claim + validation)
│   │   └── applyRulesMetadata()      ← ABSTRACT (subclase implementa)
│   ├── retrieveDocument()             ← FINAL (shared logic)
│   └── uploadDocument()              ← ABSTRACT (subclase implementa)
│
├── SoapDocumentProcessingUseCase     S3DocumentProcessingUseCase
│   ├── applyRulesMetadata()            ├── applyRulesMetadata()
│   └── uploadDocument()               └── uploadDocument()
```

### applyRulesMetadata()

Método abstracto que cada subclase implementa para validar el documento:

**SoapDocumentProcessingUseCase:**
- Si es ZIP → `processZipDocument()`
- Validar extensión y tamaño con `FileValidator`

**S3DocumentProcessingUseCase:**
- Si es ZIP → `processZipDocument()`
- Check folder exclusion (regex)
- Validar extensión y tamaño con `FileValidator`

---

## Configuración

### Variables de Entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Servicio SOAP |
| `AWS_ENDPOINT` | `` | Endpoint S3 (LocalStack) |
| `AWS_BUCKET` | `documents-bucket` | Bucket S3 |
| `AWS_REGION` | `us-east-1` | Región AWS |
| `AWS_ACCESS_KEY` | `` | Access key |
| `AWS_SECRET_KEY` | `` | Secret key |

### Configuración de Procesadores (application.yml)

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
      max-size: 52428800          # 50MB
      allowed-types: pdf,txt,csv,zip
      retry-attempts: 3
      retry-backoff-millis: 500
```

---

## Seguridad

### Protección contra ZIP Bombs

`ZipArchive.extractDocuments()` implementa límites:

- `maxEntries`: 1000 entries máximas por ZIP
- `maxUncompressedSize`: 100MB tamaño máximo descomprimido total

### Sanitización de S3 Keys

`S3GatewayAdapter.buildKey()` sanitiza el filename:
- Elimina `..` (path traversal)
- Elimina `/` y `\` (path separators)
- Previene escribir fuera del bucket

### SQL Injection

Queries parametrizados via `DatabaseClient.bind()`:
```java
// CORRECTO
databaseClient.sql("SELECT ... WHERE id = $1").bind("$1", id)
```

---

## Compilación y Ejecución

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Ejecutar con mock REST
DOCUMENT_REST_ENDPOINT=http://localhost:3001 ./gradlew bootRun

# Ejecutar con S3 mock
AWS_ENDPOINT=http://localhost:4566 ./gradlew bootRun
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

---

## Excepciones

### ProcessingException

Excepción unificada para todos los errores de procesamiento. Incluye:
- `traceId`: Extraído automáticamente del contexto reactivo via `fromContext()`
- `documentId`: Opcional
- `errorCode`: Código de error (`ProcessingResultCodes`)

**Métodos factory:**
```java
// Extrae traceId del ContextView automáticamente
ProcessingException.fromContext(ctx, message, errorCode)
ProcessingException.fromContext(ctx, message, errorCode, documentId)
ProcessingException.fromContext(ctx, message, errorCode, cause)

// Con traceId explícito
ProcessingException.withTraceId(message, errorCode, traceId)
ProcessingException.withDocumentId(message, errorCode, traceId, documentId)
```

### DomainException

Base class para todas las excepciones de dominio.

### FileValidationException

Excepción para errores de validación de archivos.

### InvalidBase64Exception

Excepción para errores en decoding Base64.
