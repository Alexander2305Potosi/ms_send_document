# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envia a un servicio SOAP externo o AWS S3.

## Arquitectura (Clean Architecture Light)

El proyecto sigue **Clean Architecture simplificada** con 2 capas principales. La capa de **dominio es pura Java** (sin dependencias de frameworks), y la capa de **infraestructura** provee los beans Spring.

```
com.example.fileprocessor/
├── domain/                    # Capa de dominio (independiente de frameworks)
│   ├── entity/               # Entidades de negocio
│   │   ├── ProductToProcess.java        # Producto en BD
│   │   ├── ProductDocumentToProcess.java # Documento de producto en BD
│   │   ├── ProductInfo.java             # Producto desde REST API
│   │   ├── ProductDocumentInfo.java    # Documento dentro de ProductInfo
│   │   ├── FileData.java               # Datos de archivo para SOAP
│   │   ├── FileUploadResult.java       # Resultado de upload
│   │   ├── SoapCommunicationLog.java    # Log de comunicacion SOAP
│   │   ├── SoapRequest.java
│   │   ├── SoapResponse.java
│   │   └── ZipArchive.java             # ZIP con documentos extraibles
│   ├── usecase/              # Casos de uso (logica de negocio)
│   │   ├── LoadProductsUseCase.java          # Carga productos y documentos
│   │   ├── AbstractProcessDocumentsUseCase.java # Procesa documentos (template method)
│   │   ├── SoapDocumentUseCase.java          # Implementacion SOAP
│   │   ├── S3DocumentUseCase.java            # Implementacion S3
│   │   ├── DocumentValidationRules.java     # Reglas de validacion
│   │   └── FileValidator.java
│   ├── util/                 # Utilidades de dominio
│   │   └── Base64Utils.java            # Encoding/decoding Base64
│   ├── valueobject/           # Value Objects (tipo seguro, validado) - Code Review #7
│   │   ├── TraceId.java              # Validación UUID y no-null
│   │   └── DocumentId.java           # Validación no-blank
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java
│   │   └── out/
│   │       ├── ProductRestGateway.java         # Puerto para API REST productos
│   │       ├── ProductRepository.java          # Puerto para productos
│   │       ├── ProductDocumentRepository.java  # Puerto para documentos de productos
│   │       ├── ExternalSoapGateway.java        # Puerto para SOAP
│   │       ├── S3Gateway.java                 # Puerto para S3
│   │       └── SoapCommunicationLogRepository.java
│   └── exception/            # Excepciones de dominio
│       ├── DomainException.java
│       └── FileValidationException.java
│
└── infrastructure/            # Capa de infraestructura (frameworks)
    ├── config/               # Configuracion (Properties, Beans)
    │   ├── DomainConfig.java          # Beans de casos de uso
    │   ├── DatabaseInitializer.java   # Schema y crash recovery
    │   └── WebFluxConfig.java
    ├── rest/                 # Adapter REST (entrada y Salida)
    │   ├── adapter/
    │   │   └── ProductRestGatewayImpl.java  # Cliente REST para productos
    │   ├── config/
    │   │   ├── DocumentRestProperties.java
    │   │   └── ProductRouterConfig.java  # RouterFunction para endpoints
    │   ├── handler/
    │   │   └── ProductHandler.java        # Logica de handlers
    │   └── exception/
    │       └── GlobalErrorHandler.java
    ├── soap/                 # Adapter SOAP (salida)
    │   ├── adapter/
    │   │   └── ExternalSoapGatewayImpl.java
    │   ├── config/
    │   │   └── SoapProperties.java
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
    └── aws/                  # Adapter AWS S3 (salida)
        ├── adapter/
        │   └── S3GatewayImpl.java
        ├── config/
        │   └── AwsConfig.java
        └── S3Properties.java
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa (puro Java, sin frameworks, sin Spring)
- **Domain** no contiene anotaciones de framework (`@Component`, `@Service`, etc.)
- **Infrastructure** depende de Domain (acceso a casos de uso y entidades)
- **Infrastructure** expone los beans de dominio via `DomainConfig.java`

## Modelo de Datos

### Producto
Un **Producto** (ej. Laptop, TV, Monitor) es la entidad raiz que contiene multiples documentos asociados.

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
| parent_document_id | VARCHAR(255) | PK del documento ZIP padre (si es hijo de ZIP) |
| filename | VARCHAR(255) | Nombre del archivo |
| content | TEXT | Contenido del archivo (Base64) |
| content_type | VARCHAR(255) | Tipo MIME |
| origin | VARCHAR(500) | Origen (ej. folderA/incoming) |
| status | VARCHAR(50) | PENDING, PROCESSING, SUCCESS, FAILURE, RETRY, SKIPPED |
| created_at | TIMESTAMP | Fecha de creacion |
| processed_at | TIMESTAMP | Fecha de procesamiento |
| trace_id | VARCHAR(255) | UUID de traza |
| soap_correlation_id | VARCHAR(255) | ID de correlacion SOAP |
| error_code | VARCHAR(100) | Codigo de error |

**soap_communication_log**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| trace_id | VARCHAR(255) | PK |
| status | VARCHAR(50) | SUCCESS o FAILURE |
| retry_count | INT | Numero de reintentos |
| error_code | VARCHAR(100) | Codigo de error |
| filename | VARCHAR(255) | Nombre del archivo |
| created_at | TIMESTAMP | Fecha del log |

## API Endpoints

### RouterFunction -> Handler -> UseCase Pattern

El servicio utiliza funciones reactivas de Spring WebFlux:
- **RouterFunction**: define las rutas y delegacion al handler
- **Handler**: contiene la logica de negocio y selecciona el use case
- **UseCase**: ejecuta la logica de procesamiento (SoapDocumentUseCase o S3DocumentUseCase)

### GET /api/v1/products/load

Carga productos y sus documentos asociados desde la API REST externa. **Los documentos ZIP son expandidos automaticamente** durante la carga, creando documentos hijos independientes por cada archivo contenido.

**Flujo de Ejecucion (Step-by-Step):**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductController.loadProducts()                                          │
│    - Genera traceId UUID                                                     │
│    - Registra traceId en MDC para logging                                    │
│    - Invoca LoadProductsUseCase.execute()                                    │
│    - Retorna HTTP 202 ACCEPTED inmediatamente (operacion async)               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. LoadProductsUseCase.execute()                                             │
│    - Genera traceId para la operacion                                        │
│    - Invoca ProductRestGateway.getAllProducts(traceId)                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. ProductRestGatewayImpl.getAllProducts()                                   │
│    - WebClient GET /api/products                                              │
│    - Header X-Trace-Id: {traceId}                                             │
│    - Parsea JSON response a List<Map<String, Object>>                        │
│    - Convierte cada Map a ProductInfo via mapToProductInfo()                 │
│    - Retorna Flux<ProductInfo>                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. LoadProductsUseCase.loadProductAndDocuments(ProductInfo)                  │
│    - Por cada ProductInfo recibido:                                           │
│      a) Crea ProductToProcess con status=PENDING                             │
│      b) Invoca createDocumentsFlux() para crear documentos                    │
│         - Si doc.isZipArchive() -> expandZipDocument()                       │
│         - Si no -> createProductDocument()                                   │
│      c) Guarda product en ProductRepository.save()                          │
│      d) Guarda todos los documentos en documentRepository.saveAll()         │
│      e) Retorna LoadProductsResult                                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. ZIP Expansion (expandZipDocument)                                         │
│    - ByteArrayInputStream del contenido ZIP                                   │
│    - ZipInputStream para iterar entradas                                     │
│    - Por cada entrada:                                                        │
│      - Extrae nombre archivo y contenido                                     │
│      - Crea ProductDocumentToProcess con:                                   │
│        - documentId: "{zipDocId}_{filename}"                                 │
│        - parentDocumentId: id del ZIP padre                                  │
│        - content: bytes del archivo                                          │
│        - contentType: detectado por nombre                                   │
│        - origin: mismo origin que el ZIP                                     │
│        - status: PENDING                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. R2dbcProductRepository.save(ProductToProcess)                            │
│    - SQL: INSERT INTO products_to_process (...) VALUES (...)                 │
│    - Bind: productId, name, status, createdAt, traceId                        │
│    - Ejecuta en H2 database via DatabaseClient                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 7. R2dbcProductDocumentRepository.saveAll(documents)                        │
│    - Flux<ProductDocumentToProcess> -> flatMap -> save()                    │
│    - Por cada documento:                                                      │
│      - SQL: INSERT INTO product_documents_to_process (...)                   │
│      - Bind: todos los campos incluyendo content (Base64)                    │
│    - Content se almacena en texto (Base64 encoded)                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 8. Respuesta al Cliente                                                      │
│    - HTTP 202 ACCEPTED                                                       │
│    - Body: { status: "LOADING", message: "...", traceId: "...", success: true }│
│    - El procesamiento real continua en background                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Response:**
```json
{
  "status": "LOADING",
  "message": "Product loading from REST API started",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true
}
```

---

### GET /api/v1/products?processor={soap|s3}

Procesa los documentos pendientes de todos los productos usando el procesador especificado (SOAP o S3). **El contenido ya esta en BD** (previamente cargado), no necesita llamar a API REST externa.

**Parametros:**
- `processor` (opcional): Tipo de procesador a usar. Valores: `soap` (default), `s3`

**Reglas de Negocio:**

1. **Tamano de archivo:** Solo archivos **< 50 MB** se envian a SOAP/S3. Archivos de 50MB o mayores se marcan como `NOT_SENT` con trazabilidad del motivo.

2. **Tipos de archivo permitidos:** Solo `pdf`, `txt`, `csv` se procesan. Otros tipos se marcan como `NOT_SENT` con el motivo.

3. **Carpetas excluidas:** Archivos en carpetas `/tmp` o `/transient` se marcan como `SKIPPED`.

4. **Patrones de origen:** Solo archivos cuyo `origin` contenga alguno de los patrones configurados en `origin-patterns-to-send` se envian a SOAP/S3. Archivos con origin que no matcheen ningun patron se marcan como `NOT_SENT`.

**Flujo de Ejecucion (Step-by-Step):**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductController.processPendingProducts()                               │
│    - Genera traceId UUID                                                     │
│    - Registra traceId en MDC                                                 │
│    - Invoca AbstractProcessDocumentsUseCase.executePendingDocuments()        │
│    - Retorna HTTP 202 ACCEPTED inmediatamente                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. AbstractProcessDocumentsUseCase.executePendingDocuments()                │
│    - Invoca documentRepository.findPendingDocuments()                        │
│    - Retorna Flux<ProductDocumentToProcess> con statuses:                    │
│      PENDING, RETRY, PROCESSING (crash recovery)                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. R2dbcProductDocumentRepository.findPendingDocuments()                    │
│    - SQL: SELECT * FROM product_documents_to_process                         │
│      WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')                     │
│      ORDER BY created_at ASC                                                │
│    - Por cada row: construye ProductDocumentToProcess                        │
│    - Decodifica content Base64 -> byte[]                                     │
│    - Retorna Flux<ProductDocumentToProcess>                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. processPendingDocument(ProductDocumentToProcess)                          │
│    - Invoca documentRepository.claimDocument(documentId)                   │
│    - Solo si claim returns TRUE continua el procesamiento                    │
│    - Si claim returns FALSE (otro proceso o no PENDING): salta documento     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. R2dbcProductDocumentRepository.claimDocument(documentId)                  │
│    - SQL: UPDATE product_documents_to_process                               │
│      SET status = 'PROCESSING', trace_id = $2, processed_at = $3          │
│      WHERE document_id = $1 AND status = 'PENDING'                          │
│    - Retorna TRUE si rowsUpdated > 0, FALSE si no hubo match                 │
│    - Este mecanismo previene duplicacion de envio                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. processDocumentClaimed(ProductDocumentToProcess)                         │
│    - Genera traceId UUID para este documento                                 │
│    - Evalua reglas de negocio en orden (usando DocumentValidationRules):     │
│                                                                             │
│    REGLA 1 - Carpeta Excluida (shouldSkipFolder):                          │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ if origin.contains("/tmp") OR origin.contains("/transient")         │  │
│    │   -> UPDATE status = 'SKIPPED', error_code = 'SKIPPED_FOLDER'        │  │
│    │   -> RETURN FileUploadResult con status=SKIPPED                     │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼                                         │
│    REGLA 2 - Patron de Origen (shouldSendByOrigin):                        │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ patterns = originPatternsToSend()  // default: ["incoming","docs"] │  │
│    │ if NOT origin.contains(any(patterns))                               │  │
│    │   -> UPDATE status = 'NOT_SENT', error_code = 'NOT_SENT_ORIGIN'    │  │
│    │   -> RETURN FileUploadResult con status=NOT_SENT                    │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼                                         │
│    REGLA 3 - Tamano de Archivo (shouldNotSendBySize):                       │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ maxSizeMb = 50 (configurable)                                      │  │
│    │ if sizeBytes >= (maxSizeMb * 1MB)                                   │  │
│    │   -> UPDATE status = 'NOT_SENT', error_code = 'SIZE_EXCEEDED'      │  │
│    │   -> RETURN FileUploadResult con status=NOT_SENT, message con tamano│  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼                                         │
│    SI TODAS LAS REGLAS PASAN -> continua a processFile()                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 7. processFile(ProductDocumentToProcess, traceId)                           │
│    - Construye FileData con: content, filename, size, contentType, traceId   │
│                                                                             │
│    REGLA 4 - Tipo de Archivo (FileValidator.validate):                      │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ allowedTypes = "pdf,txt,csv" (regex pattern)                        │  │
│    │ if NOT filename matches allowedTypes                               │  │
│    │   -> Lanza FileValidationException                                  │  │
│    │   -> En onErrorResume: status='NOT_SENT', message=validation error  │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼ (si pasa validacion)                   │
│    - Extrae folderInfo del origin (keywords: "test", "mock")                │
│      - parentFolder = parts[length-2]                                       │
│      - childFolder = parts[length-1]                                        │
│    - Construye SoapRequest via SoapRequest.fromFileData()                   │
│      - Base64.encode(content) via Base64Utils                               │
│      - Incluye parentFolder y childFolder                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 8. sendDocument(SoapRequest) - Template Method                              │
│    - Subclases SoapDocumentUseCase o S3DocumentUseCase                      │
│    - Delegan al gateway correspondiente (SOAP o S3)                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
┌─────────────────────────────┐         ┌─────────────────────────────┐
│ SoapDocumentUseCase         │         │ S3DocumentUseCase            │
│  - ExternalSoapGateway      │         │  - S3Gateway                 │
│  - soapGateway.sendFile()   │         │  - s3Gateway.upload()        │
└─────────────────────────────┘         └─────────────────────────────┘
              │                                           │
              ▼                                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 9. Respuesta y actualizacion de estado                                       │
│     - Actualiza documentRepository.updateStatus():                          │
│       UPDATE product_documents_to_process                                   │
│       SET status = 'SUCCESS',                                              │
│           soap_correlation_id = 'correlationId',                            │
│           trace_id = 'traceId',                                             │
│           processed_at = NOW()                                              │
│       WHERE document_id = 'doc-xxx'                                        │
│     - Guarda SoapCommunicationLog (success)                                 │
│     - Retorna FileUploadResult al flux                                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 10. Manejo de Errores                                                        │
│                                                                             │
│  TIMEOUT / 5xx ERROR:                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ - Reintenta hasta 3 veces con backoff exponencial                     │   │
│  │ - Si todos fallan: marca status='RETRY' o 'FAILURE'                   │   │
│  │ - Guarda SoapCommunicationLog con errorCode y retryCount               │   │
│  │ - Lanza SoapCommunicationException                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  VALIDATION ERROR:                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ - Capturado en onErrorResume de processFile                          │   │
│  │ - status='NOT_SENT', message=validation error                        │   │
│  │ - No se lanza excepcion (result es exitoso con status NOT_SENT)        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  CRASH RECOVERY:                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ - Si MS cae mientras documento estaba PROCESSING:                    │   │
│  │ - Al reiniciar: DatabaseInitializer resetea PROCESSING -> PENDING    │   │
│  │ - Solo ese documento se reprocesa (claim previene duplicados)         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Response:**
```json
{
  "status": "PROCESSING",
  "message": "Pending product documents processing started",
  "traceId": "660e8400-e29b-41d4-a716-446655440001",
  "success": true
}
```

**Response asincrono (llega via logs, no al cliente HTTP):**
```
Document processed: correlationId=abc123, status=SUCCESS
Document processed: correlationId=def456, status=NOT_SENT (file size >= 50MB)
Document processed: correlationId=ghi789, status=FAILURE (SOAP error)
```

## Procesamiento de ZIP

Los documentos ZIP son expandidos durante la carga (`/load`):
- Cada archivo extraido se guarda como documento hijo independiente
- El `parent_document_id` indica a que ZIP pertenece
- Cada hijo tiene su propio estado (`PENDING/PROCESSING/SUCCESS/FAILURE`)
- Si el sistema cae durante el envio de un hijo, solo ese hijo se reprocesa

## Estados de Documento

| Estado | Descripcion |
|--------|-------------|
| `PENDING` | Documento esperando procesamiento |
| `PROCESSING` | Documento siendo procesado |
| `SUCCESS` | Enviado exitosamente (SOAP o S3) |
| `FAILURE` | Error permanente |
| `RETRY` | Error reintentable (timeout) |
| `SKIPPED` | Saltado por regla de carpeta |
| `NOT_SENT` | No enviado (tamano >= 50MB, tipo no permitido, o origin no matchea patrones) |

## Estados de Producto (CA-01, CA-02, CA-03)

El sistema calcula automáticamente el estado del producto basado en sus documentos:

| Estado | Descripcion | Condicion |
|--------|-------------|-----------|
| `PENDING` | Producto con documentos pendientes | Al menos 1 doc PENDING/PROCESSING/RETRY |
| `SUCCESS` | Todos los documentos enviados | Todos los docs SUCCESS |
| `PARTIAL_FAILURE` | Algunos documentos fallaron | Al menos 1 doc FAILURE (pero no todos) |
| `COMPLETED_WITH_SKIPS` | Completado con documentos saltados | Todos procesados, algunos SKIPPED |
| `COMPLETED_WITH_NOT_SENT` | Completado con documentos no enviados | Todos SUCCESS/SKIPPED/NOT_SENT |
| `COMPLETED_WITH_FAILURES` | Todos los documentos fallaron | Todos los docs FAILURE |

**Lógica de Agregación:**

```java
// ProductStatusAggregator.calculateStatus(documents)
1. Si hay docs PENDING/PROCESSING/RETRY → PENDING
2. Si hay al menos 1 FAILURE → PARTIAL_FAILURE
3. Si todos SUCCESS → SUCCESS
4. Si todos SUCCESS/SKIPPED (sin FAILURE) → COMPLETED_WITH_SKIPS
5. Si todos SUCCESS/SKIPPED/NOT_SENT → COMPLETED_WITH_NOT_SENT
6. Si todos FAILURE → COMPLETED_WITH_FAILURES
```

**Actualización Automática:**
- El estado del producto se recalcula después de cada documento
- Se llama a `updateProductStatusIfComplete()` cuando el documento alcanza estado terminal
- El `SoapCommunicationLog` ahora incluye `document_id` para auditoria trazable

## Patrones de Diseño

### Template Method (AbstractProcessDocumentsUseCase)

`AbstractProcessDocumentsUseCase` implementa el flujo completo de procesamiento:

```
executePendingDocuments()
    └── findPendingDocuments()
            └── flatMap(processPendingDocument)
                    ├── claimDocument()
                    ├── validateRules()          <- reglas de negocio
                    ├── fileValidator.validate() <- tipo archivo
                    ├── sendDocument()           <- ABSTRACT (subclasses)
                    └── updateStatus()
```

Las subclases solo definen `sendDocument()` y `getImplementationName()`:
- **SoapDocumentUseCase**: envia via `ExternalSoapGateway`
- **S3DocumentUseCase**: sube via `S3Gateway`

### Record Utilities

- **DocumentValidationRules**: encapsulate validation rules as immutable record
- **Base64Utils**: utility class for Base64 encoding/decoding (no instance needed)

## Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `app.file.max-file-size-mb` | 50 | Tamano maximo para enviar |
| `app.file.allowed-types` | `pdf,txt,csv` | Tipos de archivo permitidos (regex) |
| `app.file.folders-to-skip` | `/tmp,/transient` | Carpetas a excluir |
| `app.file.origin-patterns-to-send` | `incoming,documents` | Patrones de origin que deben contener los archivos para ser enviados |

## Observabilidad

### Health Checks

El servicio expone endpoints de Actuator para monitoreo de salud:

```bash
# Health general
curl -s http://localhost:8080/actuator/health | jq .

# Readiness (verifica conexiones a BD, servicios externos)
curl -s http://localhost:8080/actuator/health/readiness | jq .

# Liveness (verifica que el MS responde)
curl -s http://localhost:8080/actuator/health/liveness | jq .
```

### Métricas (Prometheus)

```bash
# Métricas en formato Prometheus
curl -s http://localhost:8080/actuator/prometheus | jq .
```

**Métricas clave disponibles:**

| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `documents_processed_total{status}` | Counter | Documentos procesados por estado (SUCCESS/FAILURE/NOT_SENT/SKIPPED) |
| `documents_processing_duration_seconds` | Timer | Duración del procesamiento de documentos |
| `soap_retry_total` | Counter | Número total de reintentos SOAP |
| `s3_upload_total{status}` | Counter | Uploads a S3 por estado |
| `product_load_total` | Counter | Productos cargados desde REST API |

### Logs Estructurados

El servicio utiliza MDC (Mapped Diagnostic Context) para logs correlacionados:

```properties
# Formato actual (console pattern)
%-5level [%thread] %logger{36} - traceId=%X{traceId} - %msg%n
```

**Campos de log disponibles:**
- `traceId`: UUID de correlación entre operaciones
- `documentId`: ID del documento en proceso
- `correlationId`: ID de correlación SOAP (cuando aplica)

**Ejemplo de log:**
```
INFO [reactor-http-nio-2] c.e.fileprocessor.handler.ProductHandler - traceId=550e8400-e29b-41d4-a716-446655440000 - Processing 3 documents
INFO [reactor-http-nio-2] c.e.fileprocessor.usecase.SoapDocumentUseCase - traceId=660e8400-e29b-41d4-a716-446655440001 - correlationId=abc123 - Document SUCCESS
```

### Tracing Distribuido (OpenTelemetry)

Para activar tracing con Jaeger/Zipkin:

```bash
# Variables de entorno
export OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
export OTEL_SERVICE_NAME=ms_send_document
export OTEL_TRACES_SAMPLER=parentbased_traceidratio
export OTEL_METRICS_EXPORTER=none
```

**Spans generados:**
- `LoadProductsUseCase.execute` - Carga desde REST
- `AbstractProcessDocumentsUseCase.executePendingDocuments` - Procesamiento
- `ExternalSoapGateway.sendFile` - Invocación SOAP
- `S3Gateway.upload` - Upload a S3

---

## Requisitos del Sistema

| Componente | Versión | Notas |
|------------|--------|-------|
| JDK | 21+ | Requerido para Spring WebFlux 3.x |
| Gradle | 8.5+ | Wrapper incluido (`./gradlew`) |
| Docker | 24+ | Solo para ejecutar mocks |
| Base de datos | H2 (dev), PostgreSQL R2DBC (prod) | H2 en memoria para desarrollo |

## Reintentos SOAP

El servicio implementa **3 reintentos maximos** con backoff exponencial:
- **Escenarios reintentables**: Timeout (>30s), errores 5xx
- **Escenarios NO reintentables**: HTTP 4xx, errores de validación
- **Delay**: 1s, 2s, 4s entre intentos (configurable via `soap.retry-backoff-millis`)

### Tabla de Reintentos

| Intento | Delay | Acumulado |
|---------|-------|-----------|
| 1 | 1s | 1s |
| 2 | 2s | 3s |
| 3 | 4s | 7s |

## Resiliencia

### Circuit Breaker (Resilience4j) - Code Review #4

El servicio implementa Circuit Breaker con Resilience4j integrado en `AbstractProcessDocumentsUseCase`:

```java
private Mono<DocumentResult> sendDocumentWithCircuitBreaker(SoapRequest request,
    String traceId, String documentId) {
    return Mono.fromCallable(() -> request)
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .flatMap(req -> sendDocument(req))
        .onErrorResume(CallNotPermittedException.class, e -> {
            log.warn("Circuit breaker OPEN for document {}", documentId);
            return Mono.error(new SoapCommunicationException(
                "Circuit breaker is OPEN",
                DocumentErrorCodes.CIRCUIT_BREAKER_OPEN, traceId, 0));
        });
}
```

**Estados del Circuit Breaker:**
- `CLOSED`: Funcionamiento normal, solicitudes procesadas
- `OPEN`: Demasiados errores, solicitudes rejected inmediatamente con `CIRCUIT_BREAKER_OPEN`
- `HALF_OPEN`: Prueba con pocas solicitudes si el servicio recuperó

**Configuración sugerida en `application.yml`:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      SoapDocumentUseCase:  # Nombre del use case
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED
      S3DocumentUseCase:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
```

### Crash Recovery

Si el servicio cae mientras un documento estaba en procesamiento (`PROCESSING`), al reiniciar:

1. `DatabaseInitializer` detecta documentos con status `PROCESSING`
2. Los resetea a `PENDING`
3. El mecanismo de `claimDocument()` previene duplicación

```sql
-- Reset automático al iniciar
UPDATE product_documents_to_process
SET status = 'PENDING'
WHERE status = 'PROCESSING'
AND processed_at < NOW() - INTERVAL '5' MINUTE;
```

## Integración SOAP

> **Nota**: El XSD Schema no está incluido en el proyecto. El contrato SOAP está definido implícitamente por las clases en `infrastructure/soap/xml/model/`:
> - `UploadFileRequest.java`
> - `UploadFileResponse.java`

### Estructura del Request SOAP

Los campos del request se mapean desde `SoapRequest.java`:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `parentFolder` | String | Carpeta padre (ej. `incoming`) |
| `childFolder` | String | Carpeta hijo (ej. `docs`) |
| `filename` | String | Nombre del archivo |
| `fileData` | String | Contenido Base64 |
| `traceId` | String | UUID de trazabilidad |

### Ejemplo de Envelope SOAP

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:fil="http://fileprocessor.example.com/fileservice">
  <soapenv:Header>
    <fil:traceId>550e8400-e29b-41d4-a716-446655440000</fil:traceId>
  </soapenv:Header>
  <soapenv:Body>
    <fil:uploadFile>
      <fil:parentFolder>incoming</fil:parentFolder>
      <fil:childFolder>docs</fil:childFolder>
      <fil:filename>document.pdf</fil:filename>
      <fil:fileData>JVBERi0xLjQK...</fil:fileData>
    </fil:uploadFile>
  </soapenv:Body>
</soapenv:Envelope>
```

### Ejemplo de Response SOAP

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Body>
    <fil:uploadFileResponse xmlns:fil="http://fileprocessor.example.com/fileservice">
      <fil:correlationId>abc123-def456</fil:correlationId>
      <fil:status>SUCCESS</fil:status>
      <fil:message>File uploaded successfully</fil:message>
    </fil:uploadFileResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

## Ejemplos de curl

```bash
# Cargar productos desde REST API
curl -X GET http://localhost:8080/api/v1/products/load \
  -H "Accept: application/json" \
  -w "\nHTTP Status: %{http_code}\n"

# Procesar documentos pendientes (SOAP)
curl -X GET "http://localhost:8080/api/v1/products?processor=soap" \
  -H "Accept: application/json" \
  -w "\nHTTP Status: %{http_code}\n"

# Procesar documentos pendientes (S3)
curl -X GET "http://localhost:8080/api/v1/products?processor=s3" \
  -H "Accept: application/json" \
  -w "\nHTTP Status: %{http_code}\n"

# Verificar health
curl -s http://localhost:8080/actuator/health | jq .

# Ver métricas
curl -s http://localhost:8080/actuator/metrics/documents.processed.total | jq .
```

## Compilacion y Ejecucion

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Reporte de coverage
./gradlew jacocoTestReport
```

## Mocks Disponibles y Validación

### Mock REST de Productos (Java)
```bash
# Iniciar mock
./scripts/start-product-mock.sh

# Validar que está corriendo
curl -s http://localhost:3001/api/products | jq 'length'

# Expected output: array de productos
```

### Mock REST de Productos (Mockoon Desktop)
Importar `mockoon/document-rest-mock.json` en Mockoon Desktop.

**Validación del contrato:**
```bash
# Verificar estructura del mock
curl -s http://localhost:3001/api/products | jq '.[] | {productId, name, documentsCount: (.documents | length)}'
```

### Mock SOAP
```bash
# Iniciar mock
./scripts/start-mock.sh

# Validar WSDL
curl -s http://localhost:9000/soap/fileservice?wsdl | head -20

# Testear upload
curl -X POST http://localhost:9000/soap/fileservice \
  -H "Content-Type: text/xml; charset=utf-8" \
  -d '<soapenv:Envelope>...</soapenv:Envelope>'
```

### Mock S3 (LocalStack-like)
```bash
# Iniciar mock
./scripts/start-s3-mock.sh

# Validar que LocalStack está corriendo
curl -s http://localhost:4566/_localstack/health | jq .

# Listar buckets
aws --endpoint-url=http://localhost:4566 s3 ls
```

## Admin Processes

Tareas administrativas para operación y recuperación manual:

### Reset Masivo de Documentos

```bash
# Resetear todos los documentos en FAILURE/RETRY a PENDING
curl -X POST http://localhost:8080/api/v1/admin/documents/reset \
  -H "Content-Type: application/json" \
  -d '{"statuses": ["FAILURE", "RETRY"]}'
```

### Ver Estado de Cola

```bash
# Contar documentos por estado
curl -s http://localhost:8080/api/v1/admin/documents/stats | jq .
```

### Forzar Reintento de Documentos Específicos

```bash
# Reintentar un documento específico por ID
curl -X POST http://localhost:8080/api/v1/admin/documents/{documentId}/retry
```

### Migración de Base de Datos

```bash
# Ejecutar migraciones Flyway (si están configuradas)
./gradlew flywayMigrate

# Verificar schema
curl -s http://localhost:8080/actuator/health \
  | jq '.components.db.details.database'
```

## Perfiles Spring

| Perfil | Implementacion | Uso |
|--------|---------------|-----|
| `soap` (default) | SoapDocumentUseCase | Envio via SOAP |
| `s3` | S3DocumentUseCase | Upload a AWS S3 |
| sin perfil | SoapDocumentUseCase | Default igual que soap |

## Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | Endpoint de API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Endpoint del servicio SOAP |
| `AWS_ENDPOINT` | `http://localhost:4566` | Endpoint del mock S3 (LocalStack) |
| `AWS_BUCKET` | `documents-bucket` | Bucket S3 para uploads |
| `AWS_REGION` | `us-east-1` | Region AWS |

## Changelog

### 2026-04-25 - Code Review Staff Engineer - Performance & Resilience Improvements

#### Antipatterns Fixed (1-9)

**#1 - Subscribe() dentro del Pipeline Reactivo (CRÍTICO)**
- **Ubicación:** `SoapDocumentUseCase.java:39`
- **Problema:** `.subscribe()` dentro del pipeline reactivo rompe backpressure y puede perder logs
- **Solución:** Encadenar `saveSuccessLog()` con `.thenReturn(result)` como parte del flujo reactivo
- **Impacto:** Logs de éxito ahora se guardan de forma confiable

**#2 - flatMap sin Control de Concurrencia (CRÍTICO)**
- **Ubicación:** `AbstractProcessDocumentsUseCase.java:54`
- **Problema:** 10,000 documentos = 10,000 conexiones simultáneas, saturando el pool HTTP
- **Solución:** `flatMap(this::processPendingDocument, 10)` limita a 10 operaciones simultáneas
- **Impacto:** Previene saturación de conexiones y `OutOfMemoryError`

**#3 - Propagación de TraceId via MDC (ALTO)**
- **Ubicación:** `AbstractProcessDocumentsUseCase.java`
- **Problema:** TraceId como variable local, no propagable a través de operaciones async
- **Solución:** Uso de MDC (`MDC.put("traceId", ...)`) antes del flujo reactivo, limpiado con `doFinally()`
- **Impacto:** Trazabilidad completa en logs correlacionados

**#4 - Circuit Breaker con Resilience4j (ALTO)**
- **Ubicación:** `AbstractProcessDocumentsUseCase.java`
- **Problema:** Sin circuit breaker, un servicio caído causa cascading failures
- **Solución:** Integración de Resilience4j `CircuitBreakerOperator` en `sendDocumentWithCircuitBreaker()`
- **Impacto:** Previene cascadas de errores, documento marcado como `CIRCUIT_BREAKER_OPEN` cuando el CB está abierto

**#5 - Separación Domain/Infraestructura - Base64 Encoding (ALTO)**
- **Ubicación:** `SoapRequest.java`, `SoapMapper.java`, `S3GatewayImpl.java`
- **Problema:** Encoding Base64 ocurría en el domain (`SoapRequest.fromFileData()`)
- **Solución:**
  - `SoapRequest` ahora usa `byte[] fileContent` (raw bytes, sin encoding)
  - `SoapMapper.toSoapXml()` realiza el encoding en infraestructura
  - `S3GatewayImpl.upload()` usa bytes directamente
- **Impacto:** Domain puro Java, sin lógica de frameworks

**#6 - MapStruct Eliminado (MEDIO)**
- **Ubicación:** `build.gradle.kts`
- **Problema:** MapStruct en dependencias pero no se usaba
- **Solución:** Eliminado de build.gradle.kts (comentado para futuro uso)
- **Impacto:** Menos dependencias, build más limpio

**#7 - Value Objects para Campos Críticos (MEDIO)**
- **Ubicación:** Nuevo paquete `domain/valueobject/`
- **Problema:** Campos como `documentId` y `traceId` eran `String` sin validación
- **Solución:** Creados `TraceId.java` y `DocumentId.java` como records con validación
- **Impacto:** Tipo seguro y validación centralizada

**#8 - Optimización Build.gradle (MEDIO)**
- **Ubicación:** `build.gradle.kts`
- **Problema:** Build lento sin paralelismo ni cache optimizado
- **Solución:** Agregada configuración de annotation processor y JVM flags
- **Impacto:** Build incremental más rápido

**#9 - Virtual Threads Analysis (INFO)**
- **Análisis:** Virtual Threads no recomendados actualmente - modelo reactivo es válido
- **Recomendación:** Considerar Virtual Threads solo si profiling demuestra bottleneck en threads blocking

---

### Code Review: CA-01, CA-02, CA-03 - Product Status Aggregation

**CA-01 - SUCCESS cuando TODOS los documentos son SUCCESS:**
```java
// Después de procesar el último documento SUCCESS:
if (allDocumentsAreSuccess) {
    productRepository.updateStatus(productId, "SUCCESS", traceId);
}
```

**CA-02 - PARTIAL_FAILURE si al menos 1 documento falló:**
```java
// Si un documento FAILURE:
if (hasAtLeastOneFailure && !allAreFailure) {
    productRepository.updateStatus(productId, "PARTIAL_FAILURE", traceId);
}
```

**CA-03 - COMPLETED_WITH_SKIPS si todos procesados pero algunos SKIPPED:**
```java
// Si todos SUCCESS o SKIPPED (sin FAILURE):
if (allProcessed && hasSomeSkipped && noFailures) {
    productRepository.updateStatus(productId, "COMPLETED_WITH_SKIPS", traceId);
}
```

**Implementación en AbstractProcessDocumentsUseCase:**
```java
private Mono<Void> updateProductStatusIfComplete(String productId, String traceId) {
    return documentRepository.findByProductId(productId)
        .collectList()
        .flatMap(docs -> {
            ProductStatus newStatus = ProductStatusAggregator.calculateStatus(docs);
            boolean shouldUpdate = docs.stream()
                .allMatch(doc -> isTerminalStatus(doc.getStatus()));
            if (shouldUpdate) {
                return productRepository.updateStatus(productId, newStatus.name(), traceId);
            }
            return Mono.empty();
        });
}
```

---

### Code Review: Estructura de Archivos Actualizada

```
com.example.fileprocessor/
├── domain/
│   ├── valueobject/              # NUEVO: Value Objects para tipo seguro
│   │   ├── TraceId.java          # Validación de UUID y no-null
│   │   └── DocumentId.java       # Validación de no-blank
│   ├── entity/
│   │   ├── ProductStatus.java    # NUEVO: Estados de producto
│   │   └── SoapCommunicationLog.java  # MODIFICADO: +documentId
│   ├── usecase/
│   │   ├── ProductStatusAggregator.java  # NUEVO: Agregación de estado
│   │   └── ProductStatusSummary.java      # NUEVO: Resumen de estado
│   └── entity/
│       └── SoapRequest.java      # MODIFICADO: fileContent bytes (no Base64)
...
└── infrastructure/
    ├── helpers/soap/
    │   └── mapper/
    │       └── SoapMapper.java    # MODIFICADO: Base64 encoding aquí
    └── drivenadapters/aws/
        └── S3GatewayImpl.java     # MODIFICADO: bytes directos
```

---

### Code Review: Flujo Reactivo Corregido

```java
// ANTES (ANTI-PATRÓN)
.flatMap(this::processPendingDocument)  // Sin límite
.doOnNext(result -> saveLog().subscribe())  // subscribe() rompe backpressure

// DESPUÉS (CORRECTO)
.flatMap(this::processPendingDocument, 10)  // maxConcurrency = 10
.flatMap(result -> saveLog().thenReturn(result))  // Encadenado correctamente
```

---

### Code Review: Circuit Breaker Integration

```java
private Mono<DocumentResult> sendDocumentWithCircuitBreaker(SoapRequest request, ...) {
    return Mono.fromCallable(() -> request)
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .flatMap(req -> sendDocument(req))
        .onErrorResume(CallNotPermittedException.class, e -> {
            log.warn("Circuit breaker OPEN for document {}", documentId);
            return Mono.error(new SoapCommunicationException(
                "Circuit breaker is OPEN",
                DocumentErrorCodes.CIRCUIT_BREAKER_OPEN, traceId, 0));
        });
}
```

---

### Code Review: TraceId Propagation

```java
public Flux<FileUploadResult> executePendingDocuments() {
    String rootTraceId = UUID.randomUUID().toString();
    MDC.put("traceId", rootTraceId);  // Establecer al inicio

    return documentRepository.findPendingDocuments()
        .flatMap(this::processPendingDocument, DEFAULT_MAX_CONCURRENCY)
        .doFinally(signal -> MDC.remove("traceId"));  // Limpiar al final
}

private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
    String traceId = UUID.randomUUID().toString();
    MDC.put("traceId", traceId);  // Cada documento con su trace
    // ... procesamiento
    .doFinally(signal -> MDC.remove("traceId"));
}
```

---

### Tests de Recuperación (Crash Recovery)

```java
@Test
void shouldResumeFromFailedDocumentOnRestart() {
    // GIVEN: 5 documentos, el documento #3 falla
    when(documentRepository.findPendingDocuments())
        .thenReturn(Flux.just(doc1, doc2, doc3, doc4, doc5));

    // Doc1, Doc2 succeed, Doc3 falla con timeout
    when(soapGateway.sendFile(any()))
        .thenReturn(Mono.just(successResponse("corr-1")))
        .thenReturn(Mono.just(successResponse("corr-2")))
        .thenReturn(Mono.error(new SoapCommunicationException("Timeout")))
        .thenReturn(Mono.just(successResponse("corr-4")))
        .thenReturn(Mono.just(successResponse("corr-5")));

    // WHEN: Primera ejecución
    useCase.executePendingDocuments().block();

    // THEN: Doc3 quedó en RETRY
    verify(documentRepository).updateStatus(
        eq("doc-3"), eq("RETRY"), anyString(), isNull(), eq("GATEWAY_TIMEOUT"));

    // WHEN: Simular restart (claim returns FALSE para PENDING=false)
    when(documentRepository.claimDocument("doc-3"))
        .thenReturn(false)  // Primero no puede (aún RETRY)
        .thenReturn(true);  // Luego se reclama

    // WHEN: Segunda ejecución
    useCase.executePendingDocuments().block();

    // THEN: Solo se procesó doc-3
    verify(soapGateway, times(1)).sendFile(any());
}
```

---

### 2026-04-25 - Refactorizacion + S3 Support
- **Refactor:** `ProcessProductDocumentsUseCase` eliminado, logica movida a `AbstractProcessDocumentsUseCase`
- **Refactor:** Creado `DocumentValidationRules` para encapsular reglas de validacion
- **Refactor:** Creado `Base64Utils` utility para encoding/decoding
- **Nuevo:** `S3DocumentUseCase` para uploads a AWS S3
- **Nuevo:** Perfil Spring "s3" para activar procesamiento via S3
- **Actualizado:** `SoapDocumentUseCase` y `S3DocumentUseCase` ahora extienden `AbstractProcessDocumentsUseCase`

### 2026-04-25 - Refactorizacion Product-Centric + S3 Support
- **Nuevo:** Entidades ProductToProcess, ProductDocumentToProcess, ProductInfo, ProductDocumentInfo
- **Nuevo:** Puertos ProductRestGateway, ProductRepository, ProductDocumentRepository
- **Nuevo:** Use cases LoadProductsUseCase, ProcessProductDocumentsUseCase
- **Nuevo:** Controlador ProductController con endpoints /api/v1/products/load y /api/v1/products
- **Nuevo:** Mock ProductRestMock y script start-product-mock.sh
- **Nuevo:** S3Gateway port y S3GatewayImpl adapter para uploads a AWS S3
- **Nuevo:** S3Mock y script start-s3-mock.sh para testing local (simula LocalStack)
- **Nuevo:** Perfil Spring "s3" para activar procesamiento via S3 en lugar de SOAP
- **Eliminado:** FileController, LoadDocumentsUseCase, ProcessFileUseCase (dominio anterior)
- **Actualizado:** DatabaseInitializer con nuevas tablas y crash recovery
- **Actualizado:** Postman y Mockoon con nueva estructura de productos