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
