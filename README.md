# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envia a un servicio SOAP externo.

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
│   │   ├── ProcessProductDocumentsUseCase.java # Procesa documentos pendientes
│   │   └── FileValidator.java
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java
│   │   └── out/
│   │       ├── ProductRestGateway.java         # Puerto para API REST productos
│   │       ├── ProductRepository.java          # Puerto para productos
│   │       ├── ProductDocumentRepository.java  # Puerto para documentos de productos
│   │       └── ExternalSoapGateway.java
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
    │   │   └── DocumentRestProperties.java
    │   ├── controller/
    │   │   └── ProductController.java
    │   └── exception/
    │       └── GlobalErrorHandler.java
    └── soap/                 # Adapter SOAP (salida)
        ├── adapter/
        │   └── ExternalSoapGatewayImpl.java
        ├── config/
        │   └── SoapProperties.java
        ├── mapper/
        │   └── SoapMapper.java
        ├── xml/
        │   ├── SoapEnvelopeWrapper.java
        │   ├── SoapNamespaces.java
        │   └── model/
        │       ├── UploadFileRequest.java
        │       └── UploadFileResponse.java
        └── exception/
            └── SoapCommunicationException.java
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
│         - Si doc.isZipArchive() -> expandZipDocument()                        │
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

### GET /api/v1/products

Procesa los documentos pendientes de todos los productos. **El contenido ya esta en BD** (previamente cargado), no necesita llamar a API REST externa.

**Reglas de Negocio:**

1. **Tamano de archivo:** Solo archivos **< 50 MB** se envian a SOAP. Archivos de 50MB o mayores se marcan como `NOT_SENT` con trazabilidad del motivo.

2. **Tipos de archivo permitidos:** Solo `pdf`, `txt`, `csv` se procesan. Otros tipos se marcan como `NOT_SENT` con el motivo.

3. **Carpetas excluidas:** Archivos en carpetas `/tmp` o `/transient` se marcan como `SKIPPED`.

4. **Patrones de origen:** Solo archivos cuyo `origin` contenga alguno de los patrones configurados en `origin-patterns-to-send` se envian a SOAP. Archivos con origin que no matcheen ningun patron se marcan como `NOT_SENT`.

**Flujo de Ejecucion (Step-by-Step):**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductController.processPendingProducts()                               │
│    - Genera traceId UUID                                                     │
│    - Registra traceId en MDC                                                 │
│    - Invoca ProcessProductDocumentsUseCase.executePendingDocuments()         │
│    - Retorna HTTP 202 ACCEPTED inmediatamente                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. ProcessProductDocumentsUseCase.executePendingDocuments()                  │
│    - Invoca documentRepository.findPendingDocuments()                        │
│    - Retorna Flux<ProductDocumentToProcess> con statuses:                   │
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
│      SET status = 'PROCESSING', trace_id = $2, processed_at = $3            │
│      WHERE document_id = $1 AND status = 'PENDING'                          │
│    - Retorna TRUE si rowsUpdated > 0, FALSE si no hubo match                 │
│    - Este mecanismo previene duplicacion de envio                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. processDocumentClaimed(ProductDocumentToProcess)                         │
│    - Genera traceId UUID para este documento                                 │
│    - Evalua reglas de negocio en orden:                                      │
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
│    │   -> UPDATE status = 'NOT_SENT', error_code = 'SIZE_EXCEEDED'       │  │
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
│    - Construye SoapRequest via SoapRequest.fromFileData()                  │
│      - Base64.encode(content)                                              │
│      - Incluye parentFolder y childFolder                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 8. ExternalSoapGatewayImpl.sendFile(SoapRequest)                            │
│    - SoapMapper.toFullSoapMessage(request) -> XML completo SOAP             │
│    - WebClient.post() al endpoint SOAP                                      │
│    - Header: SOAPAction = "fileService/UploadFile"                          │
│    - Content-Type: TEXT_XML                                                 │
│    - Timeout: configurable (default 30s)                                    │
│    - Retry: backoff 3 intentos (1s, 2s, 4s)                                 │
│    - Filtro de reintento: TimeoutException, 5xx Server Error                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 9. SoapMapper.toFullSoapMessage(SoapRequest)                                 │
│    - Crea UploadFileRequest JAXB object                                     │
│      - fileContentBase64: Base64 encoded content                            │
│      - filename, contentType, fileSize                                      │
│      - traceId, timestamp                                                   │
│      - parentFolder, childFolder                                            │
│    - Marshalls a XML con format:                                           │
│      <file:UploadFileRequest>                                              │
│        <file:fileContentBase64>...</file:fileContentBase64>                │
│        <file:filename>...</file:filename>                                   │
│        ...                                                                 │
│      </file:UploadFileRequest>                                             │
│    - Envuelve en envelope SOAP:                                             │
│      <?xml version="1.0"?>                                                  │
│      <soap:Envelope>                                                        │
│        <soap:Body>                                                          │
│          ...upload request...                                               │
│        </soap:Body>                                                         │
│      </soap:Envelope>                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 10. Respuesta SOAP y actualizacion de estado                                 │
│     - SOAP Response parseado via SoapMapper.fromSoapXml()                   │
│     - Extrae correlationId, status, message                                 │
│     - Invoca documentRepository.updateStatus():                             │
│       UPDATE product_documents_to_process                                   │
│       SET status = 'SUCCESS',                                              │
│           soap_correlation_id = 'correlationId',                            │
│           trace_id = 'traceId',                                             │
│           processed_at = NOW()                                              │
│       WHERE document_id = 'doc-xxx'                                         │
│     - Guarda SoapCommunicationLog (success)                                 │
│     - Retorna FileUploadResult al flux                                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 11. Manejo de Errores                                                        │
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
| `SUCCESS` | Enviado a SOAP exitosamente |
| `FAILURE` | Error permanente en SOAP |
| `RETRY` | Error reintentable (timeout) |
| `SKIPPED` | Saltado por regla de carpeta |
| `NOT_SENT` | No enviado (tamano >= 50MB, tipo no permitido, o origin no matchea patrones) |

## Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `app.file.max-file-size-mb` | 50 | Tamano maximo para enviar a SOAP |
| `app.file.allowed-types` | `pdf,txt,csv` | Tipos de archivo permitidos (regex) |
| `app.file.folders-to-skip` | `/tmp,/transient` | Carpetas a excluir |
| `app.file.origin-patterns-to-send` | `incoming,documents` | Patrones de origin que deben contener los archivos para ser enviados |

## Reintentos SOAP

El servicio implementa **3 reintentos maximos** con backoff exponencial:
- **Escenarios reintentables**: Timeout, errores 5xx
- **Delay**: 1s, 2s, 4s entre intentos

## Compilacion y Ejecucion

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Reporte de coverage
./gradlew jacocoTestReport
```

## Mocks Disponibles

### Mock REST de Productos (Java)
```bash
./scripts/start-product-mock.sh
```

### Mock REST de Productos (Mockoon Desktop)
Importar `mockoon/document-rest-mock.json` en Mockoon Desktop.

### Mock SOAP
```bash
./scripts/start-mock.sh
```

## Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | Endpoint de API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Endpoint del servicio SOAP |

## Changelog

### 2026-04-25 - Refactorizacion Product-Centric
- **Nuevo:** Entidades ProductToProcess, ProductDocumentToProcess, ProductInfo, ProductDocumentInfo
- **Nuevo:** Puertos ProductRestGateway, ProductRepository, ProductDocumentRepository
- **Nuevo:** Use cases LoadProductsUseCase, ProcessProductDocumentsUseCase
- **Nuevo:** Controlador ProductController con endpoints /api/v1/products/load y /api/v1/products
- **Nuevo:** Mock ProductRestMock y script start-product-mock.sh
- **Eliminado:** FileController, LoadDocumentsUseCase, ProcessFileUseCase (dominio anterior)
- **Actualizado:** DatabaseInitializer con nuevas tablas y crash recovery
- **Actualizado:** Postman y Mockoon con nueva estructura de productos
