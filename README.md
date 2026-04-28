# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envía a un servicio SOAP externo o AWS S3.

## Arquitectura (Clean Architecture Light)

El proyecto sigue **Clean Architecture simplificada** con 2 capas principales. La capa de **dominio es pura Java** (sin dependencias de frameworks), y la capa de **infraestructura** provee los beans Spring.

```
com.example.fileprocessor/
├── domain/                          # Capa de dominio (independiente de frameworks)
│   ├── entity/                      # Entidades de negocio
│   │   ├── ProductToProcess.java            # Producto en BD
│   │   ├── ProductDocumentToProcess.java    # Documento de producto en BD
│   │   ├── ProductInfo.java                 # Producto desde REST API
│   │   ├── ProductDocumentInfo.java          # Documento dentro de ProductInfo
│   │   ├── FileUploadResult.java            # Resultado de upload/procesamiento
│   │   ├── ExternalServiceResponse.java     # Respuesta generica de servicio externo
│   │   ├── DocumentSendRequest.java         # Request de envio
│   │   ├── ZipArchive.java                  # ZIP con documentos extraibles
│   │   ├── AsyncOperationStatus.java        # Tracking de operacion async
│   │   ├── DocumentStatus.java              # Estados de documento (constantes)
│   │   └── ProductStatus.java               # Estados de producto
│   ├── usecase/                      # Casos de uso — Template Method Pattern
│   │   ├── AbstractDocumentProcessingUseCase.java  # Clase abstracta base
│   │   ├── SoapDocumentProcessingUseCase.java      # Subclase SOAP
│   │   ├── S3DocumentProcessingUseCase.java        # Subclase S3
│   │   ├── LoadProductsUseCase.java                # Carga productos y documentos
│   │   ├── LoadProductsResult.java                 # Resultado de carga
│   │   ├── ClaimResult.java                        # Resultado de claim atomico
│   │   ├── FileValidator.java                      # Validador de archivos
│   │   ├── ProductStatusAggregator.java            # Agregacion de estado de producto
│   │   ├── ProductStatusSummary.java               # Resumen de estado
│   │   └── ProcessingResultCodes.java              # Codigos de resultado
│   ├── util/                        # Utilidades de dominio
│   │   ├── Base64Utils.java                  # Encoding/decoding Base64 (+decodeSafe)
│   │   └── MediaTypeConstants.java           # Constantes de tipos MIME
│   ├── valueobject/                  # Value Objects (tipo seguro, validado)
│   │   └── FolderExclusionRegexConfig.java     # Patrones regex de exclusion de carpetas (fail-fast)
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java       # Configuracion de validacion
│   │   └── out/
│   │       ├── ProductRestGateway.java          # Puerto para API REST productos
│   │       ├── ProductRepository.java            # Puerto para productos
│   │       ├── ProductDocumentRepository.java    # Puerto para documentos (+claimDocument)
│   │       ├── FileGateway.java                 # Puerto unificado para envio (SOAP y S3)
│   │       └── AsyncOperationRepository.java     # Puerto para tracking async
│   └── exception/                   # Excepciones de dominio
│       ├── DomainException.java
│       ├── FileValidationException.java
│       ├── CommunicationException.java
│       └── InvalidBase64Exception.java
│
├── application/                      # Configuracion de aplicacion
│   └── app-service/
│       └── config/
│           └── DomainConfig.java             # Beans de casos de uso (~65 lineas)
│
└── infrastructure/                  # Capa de infraestructura (frameworks)
    ├── entrypoints/
    │   └── rest/                    # Adapter REST (entrada)
    │       ├── ProductRoutes.java             # RouterFunction para endpoints
    │       ├── handler/
    │       │   └── ProductHandler.java         # Logica de handlers
    │       ├── config/
    │       │   └── DocumentRestProperties.java
    │       └── constants/
    │           ├── RestApiPaths.java           # Constantes de paths
    │           └── ApiConstants.java           # Constantes varias
    ├── drivenadapters/               # Adapters (implementaciones)
    │   ├── rest-client/
    │   │   └── ProductRestGatewayAdapter.java # Cliente REST para productos
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java        # Gateway SOAP
    │   │   └── config/
    │   │       └── SoapProperties.java        # Configuracion SOAP
    │   ├── aws/
    │   │   ├── S3GatewayAdapter.java          # Adapter S3
    │   │   └── config/
    │   │       ├── AwsConfig.java
    │   │       └── S3Properties.java
    │   ├── r2dbc/
    │   │   ├── R2dbcProductRepository.java
    │   │   └── R2dbcProductDocumentRepository.java
    │   └── async/
    │       └── InMemoryAsyncOperationRepository.java  # Repo in-memory para tracking
    └── helpers/
        ├── config/
        │   ├── ProcessorConfig.java           # Configuracion unificada de procesadores (SOAP y S3)
        │   ├── ProcessorSettings.java         # Settings individuales por procesador
        │   ├── FileUploadProperties.java
        │   └── WebFluxConfig.java             # Configuracion de WebFlux
        ├── shutdown/
        │   └── GracefulShutdownManager.java   # Manejo de graceful shutdown
        └── soap/
            ├── mapper/
            │   └── SoapMapper.java             # fromSoapXml() retorna FileUploadResult
            ├── xml/
            │   ├── SoapEnvelopeWrapper.java
            │   └── SoapNamespaces.java
            │   └── model/
            │       ├── UploadFileRequest.java
            │       └── UploadFileResponse.java
            └── exception/
                └── SoapCommunicationException.java
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa (puro Java, sin frameworks, sin Spring)
- **Domain** no contiene anotaciones de framework (`@Component`, `@Service`, etc.)
- **Infrastructure** y **Application** dependen de Domain
- **Application** expone los beans de dominio via `DomainConfig.java`

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
| content | TEXT | Contenido del archivo (Base64 validado con decodeSafe) |
| content_type | VARCHAR(255) | Tipo MIME |
| origin | VARCHAR(500) | Origen (ej. folderA/incoming) |
| status | VARCHAR(50) | PENDING, PROCESSING, SUCCESS, FAILURE, RETRY, SKIPPED, NOT_SENT |
| created_at | TIMESTAMP | Fecha de creacion |
| processed_at | TIMESTAMP | Fecha de procesamiento |
| trace_id | VARCHAR(255) | UUID de traza |
| correlation_id | VARCHAR(255) | ID de correlacion (SOAP correlationId o S3 eTag) |
| error_code | VARCHAR(100) | Codigo de error |

## API Endpoints

### RouterFunction -> Handler -> UseCase Pattern

El servicio utiliza funciones reactivas de Spring WebFlux:
- **RouterFunction**: define las rutas y delegacion al handler
- **Handler**: contiene la logica de negocio y selecciona el use case
- **UseCase**: ejecuta la logica de procesamiento (SoapDocumentProcessingUseCase o S3DocumentProcessingUseCase)

### GET /api/v1/products/load

Carga productos y sus documentos asociados desde la API REST externa. **Los documentos ZIP son expandidos automaticamente** durante la carga, creando documentos hijos independientes por cada archivo contenido.

**Flujo de Ejecucion (Step-by-Step):**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductHandler.loadProducts()                                           │
│    - Genera traceId UUID                                                    │
│    - Crea AsyncOperationStatus (LOADING)                                   │
│    - Guarda estado en AsyncOperationRepository                             │
│    - Retorna HTTP 202 ACCEPTED inmediatamente (operacion async)             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. LoadProductsUseCase.execute()                                          │
│    - Invoca ProductRestGateway.getAllProducts(traceId)                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. ProductRestGatewayAdapter.getAllProducts()                              │
│    - WebClient GET /api/products                                            │
│    - Header X-Trace-Id: {traceId}                                            │
│    - Parsea JSON response a List<Map<String, Object>>                       │
│    - Convierte cada Map a ProductInfo via mapToProductInfo()               │
│    - Retorna Flux<ProductInfo>                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. LoadProductsUseCase.loadProductAndDocuments(ProductInfo)                 │
│    - Por cada ProductInfo recibido:                                          │
│      a) Crea ProductToProcess con status=PENDING                            │
│      b) Invoca createDocumentsFlux() para crear documentos                   │
│         - Si doc.isZipArchive() -> expandZipDocument()                      │
│         - Si no -> createProductDocument()                                  │
│      c) Guarda product en ProductRepository.save()                          │
│      d) Guarda todos los documentos en documentRepository.saveAll()         │
│      e) Retorna LoadProductsResult                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. ZIP Expansion (expandZipDocument)                                        │
│    - ByteArrayInputStream del contenido ZIP                                  │
│    - ZipInputStream para iterar entradas                                     │
│    - Por cada entrada:                                                       │
│      - Extrae nombre archivo y contenido                                      │
│      - Crea ProductDocumentToProcess con:                                    │
│        - documentId: "{zipDocId}_{filename}"                                 │
│        - parentDocumentId: id del ZIP padre                                  │
│        - content: bytes del archivo                                          │
│        - contentType: detectado por nombre                                   │
│        - origin: mismo origin que el ZIP                                     │
│        - status: PENDING                                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. R2dbcProductRepository.save(ProductToProcess)                           │
│    - SQL: INSERT INTO products_to_process (...) VALUES (...)                 │
│    - Bind: productId, name, status, createdAt, traceId                      │
│    - Ejecuta en H2 database via DatabaseClient                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 7. R2dbcProductDocumentRepository.saveAll(documents)                        │
│    - Flux<ProductDocumentToProcess> -> flatMap -> save()                     │
│    - Por cada documento:                                                     │
│      - SQL: INSERT INTO product_documents_to_process (...)                   │
│      - Bind: todos los campos incluyendo content (Base64)                   │
│    - Content se almacena en texto (Base64 encoded)                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

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

---

### GET /api/v1/products?processor={soap|s3}

Procesa los documentos pendientes de todos los productos usando el procesador especificado (SOAP o S3). **El contenido ya esta en BD** (previamente cargado), no necesita llamar a API REST externa.

**Parametros:**
- `processor` (opcional): Tipo de procesador a usar. Valores: `soap` (default), `s3`

**Reglas de Negocio:**

1. **Tamano de archivo:** Solo archivos dentro del limite configurado se envian. Archivos que excedan el tamano se marcan como `NOT_SENT`.

2. **Tipos de archivo permitidos:** Solo los tipos configurados se procesan. Otros tipos se marcan como `NOT_SENT`.

3. **Carpetas excluidas:** Archivos en carpetas configuradas como excluidas se marcan como `SKIPPED`.

4. **Patrones de origen:** Solo archivos cuyo `origin` contenga alguno de los patrones configurados en `origin-patterns-to-send` se envian.

5. **Keywords:** Archivos cuyo nombre contenga keywords configuradas pueden ser excluidos.

**Flujo de Ejecucion (Step-by-Step):**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. ProductHandler.processPendingProducts()                                  │
│    - Resuelve el UseCase segun parametro processor                           │
│    - Genera traceId UUID                                                     │
│    - Crea AsyncOperationStatus (PROCESSING)                                 │
│    - Retorna HTTP 202 ACCEPTED inmediatamente                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. AbstractDocumentProcessingUseCase.executePendingDocuments()              │
│    - Invoca documentRepository.findPendingDocuments()                        │
│    - Retorna Flux<ProductDocumentToProcess> con statuses:                    │
│      PENDING, RETRY, PROCESSING (crash recovery)                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. R2dbcProductDocumentRepository.findPendingDocuments()                     │
│    - SQL: SELECT * FROM product_documents_to_process                        │
│      WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')                      │
│      ORDER BY created_at ASC                                                 │
│    - Por cada row: construye ProductDocumentToProcess                         │
│    - Decodifica content Base64 -> byte[]                                     │
│    - Retorna Flux<ProductDocumentToProcess>                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. processPendingDocument(ProductDocumentToProcess)  [TEMPLATE METHOD]       │
│    - Genera traceId UUID por documento                                       │
│    - Invoca documentRepository.claimDocument(documentId)                     │
│    - claimDocument(): UPDATE atomico con condicion WHERE                    │
│      status IN ('PENDING', 'RETRY', 'PROCESSING')                           │
│    - Si retorna TRUE: continua el procesamiento                             │
│    - Si retorna FALSE (otro pod lo tomo): salta documento                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. Template Method: processDocumentInternal()                               │
│                                                                             │
│    Paso 1: prepareDocument() → ABSTRACT (subclase):                         │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ [SOAP]: fileValidator.validate() - validacion de archivo            │  │
│    │ [S3]:   fileValidator.validate() + folderExclusionRegex             │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│    Paso 2: buildRequest() → CONCRETO compartido:                            │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ Construye DocumentSendRequest con:                                   │  │
│    │ - documentId, fileContent, filename, contentType, fileSize          │  │
│    │ - traceId, parentFolder, childFolder (extraidos del origin)         │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│    Paso 3: sendWithResilience() → CONCRETO compartido:                      │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ - fileGateway.send(request)                                          │  │
│    │ - onErrorResume: captura excepcion y construye FAILURE result       │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│    Paso 4: checkpoint() → CONCRETO compartido:                              │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ UPDATE product_documents_to_process                                  │  │
│    │ SET status, trace_id, processed_at, correlation_id, error_code      │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                        │
│    Paso 5: postProcess() → CONCRETO compartido:                             │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │ - statusAggregator.updateProductStatus(productId, traceId)          │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
┌─────────────────────────────┐         ┌─────────────────────────────┐
│ SoapDocumentProcessing      │         │ S3DocumentProcessing          │
│ UseCase                     │         │ UseCase                       │
│  - fileValidator.validate() │         │  - fileValidator.validate()   │
│  - SoapGatewayAdapter       │         │  - folderExclusionRegex       │
│                              │         │  - S3GatewayAdapter           │
└─────────────────────────────┘         └─────────────────────────────┘
              │                                           │
              ▼                                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. Manejo de Errores                                                        │
│                                                                             │
│  GATEWAY ERROR:                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ - sendWithResilience() captura excepcion via onErrorResume           │   │
│  │ - Construye FileUploadResult con status=FAILURE                      │   │
│  │ - Extrae errorCode de CommunicationException o usa UNKNOWN_ERROR     │   │
│  │ - checkpoint() guarda status=FAILURE con error_code                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  VALIDATION ERROR:                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ - Capturado en prepareDocument() via FileValidator                   │   │
│  │ - Status = 'NOT_SENT' o 'SKIPPED', segun regla                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  CRASH RECOVERY (DatabaseInitializer):                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ - Al iniciar: UPDATE product_documents_to_process                    │   │
│  │   SET status = 'PENDING' WHERE status = 'PROCESSING'                │   │
│  │ - Documentos atascados vuelven a estar disponibles                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

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

### GET /api/v1/operations/{traceId}/status

Consulta el estado de una operacion asincrona (carga o procesamiento).

**Response:**
```json
{
  "traceId": "660e8400-e29b-41d4-a716-446655440001",
  "operationType": "PROCESS",
  "status": "PROCESSING",
  "message": "Pending product documents processing started",
  "totalItems": 0,
  "processedItems": 5,
  "successItems": 4,
  "failedItems": 1,
  "startedAt": "2026-04-27T12:00:00Z",
  "completedAt": null,
  "success": true
}
```

**Estados de operacion:**
- `LOADING`: Cargando productos desde REST API
- `PROCESSING`: Procesando documentos
- `COMPLETED`: Operacion finalizada

**Tracking de progreso:**
El handler actualiza el `AsyncOperationStatus` con el progreso:
- `totalItems`: Total de items a procesar
- `processedItems`: Items procesados hasta el momento
- `successItems`: Items exitosos
- `failedItems`: Items fallidos

---

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
| `NOT_SENT` | No enviado (tamano excedido, tipo no permitido, o origin no matchea patrones) |

## Estados de Producto

El sistema calcula automaticamente el estado del producto basado en sus documentos:

| Estado | Descripcion | Condicion |
|--------|-------------|-----------|
| `PENDING` | Producto con documentos pendientes | Al menos 1 doc PENDING/PROCESSING/RETRY |
| `SUCCESS` | Todos los documentos enviados | Todos los docs SUCCESS |
| `PARTIAL_FAILURE` | Algunos documentos fallaron | Al menos 1 doc FAILURE (pero no todos) |
| `COMPLETED_WITH_SKIPS` | Completado con documentos saltados | Todos procesados, algunos SKIPPED |
| `COMPLETED_WITH_NOT_SENT` | Completado con documentos no enviados | Todos SUCCESS/SKIPPED/NOT_SENT |
| `COMPLETED_WITH_FAILURES` | Todos los documentos fallaron | Todos los docs FAILURE |

**Logica de Agregacion:**

```java
// ProductStatusAggregator.calculateStatus(documents)
1. Si hay docs PENDING/PROCESSING/RETRY → PENDING
2. Si hay al menos 1 FAILURE → PARTIAL_FAILURE
3. Si todos SUCCESS → SUCCESS
4. Si todos SUCCESS/SKIPPED (sin FAILURE) → COMPLETED_WITH_SKIPS
5. Si todos SUCCESS/SKIPPED/NOT_SENT → COMPLETED_WITH_NOT_SENT
6. Si todos FAILURE → COMPLETED_WITH_FAILURES
```

**Actualizacion Automatica:**
- El estado del producto se recalcula despues de cada documento
- Se llama a `updateProductStatusIfComplete()` cuando el documento alcanza estado terminal

## Patrones de Diseno

### Template Method Pattern (AbstractDocumentProcessingUseCase)

El procesamiento de documentos usa **Template Method** con clase abstracta base y subclases especializadas:

```
AbstractDocumentProcessingUseCase (abstract class, domain layer)
  ├── executePendingDocuments()          ← FINAL (orquestacion)
  └── processDocumentInternal(doc, id)   ← FINAL (template method)
       ├── 1. prepareDocument()          ← ABSTRACT (subclase: validacion + filtrado)
       ├── 2. buildRequest()             ← CONCRETO compartido (DocumentSendRequest)
       ├── 3. sendWithResilience()       ← CONCRETO compartido (envio + error handling)
       ├── 4. checkpoint()               ← CONCRETO compartido (update BD)
       └── 5. postProcess()              ← CONCRETO compartido (agregacion producto)
            ↑                          ↑
            │                          │
SoapDocumentProcessingUseCase    S3DocumentProcessingUseCase
  - prepareDocument()              - prepareDocument()
  - implementationName()           - implementationName()
```

**Constructor:**
```java
// 4 dependencias compartidas via @AllArgsConstructor
protected final ProductDocumentRepository documentRepository;
protected final ProductStatusAggregator statusAggregator;
protected final FileGateway fileGateway;
protected final FileValidator fileValidator;
```

**Beneficios:**
- **Responsabilidad unica**: cada subclase implementa solo 2 metodos
- **Resiliencia uniforme**: manejo de errores en un solo lugar
- **Extensibilidad**: nuevo gateway (FTP, REST) = nueva subclase con 2 metodos
- **DomainConfig simplificado**: ~65 lineas, beans declarativos

### Pipeline del Template Method

El flujo se divide en 5 pasos con responsabilidades claras:

```
1. prepareDocument    → ABSTRACT: validacion especifica + filtrado por gateway
2. buildRequest       → CONCRETO: construir DocumentSendRequest con folder info
3. sendWithResilience → CONCRETO: enviar via FileGateway + onErrorResume
4. checkpoint         → CONCRETO: guardar estado terminal en BD
5. postProcess        → CONCRETO: agregar estado de producto
```

**Codigo ejemplo:**
```java
protected final Mono<FileUploadResult> processDocumentInternal(
        ProductDocumentToProcess pending, String traceId) {

    Mono<DocumentSendRequest> request = prepareDocument(pending, traceId)
        .flatMap(doc -> buildRequest(doc, traceId));

    return request
        .flatMap(this::sendWithResilience)
        .flatMap(result -> checkpoint(pending, result, traceId))
        .flatMap(result -> postProcess(pending, result, traceId));
}
```

### Async Operation Tracking

El servicio utiliza `AsyncOperationRepository` para tracking de operaciones asincronas:
- `InMemoryAsyncOperationRepository`: implementacion en memoria
- Permite consultar el estado via `/api/v1/operations/{traceId}/status`
- Actualiza progreso en cada documento procesado

### Records y Value Objects

- **ClaimResult**: resultado de operacion claim atomico (claimed, previousStatus)
- **LoadProductsResult**: resultado de operacion de carga
- **DocumentSendRequest**: request de envio con campos agrupados (builder pattern)
- **FolderExclusionRegexConfig**: compila y valida patrones regex al iniciar (fail-fast)
- **Base64Utils**: utility class para Base64 encoding/decoding (+decodeSafe con contexto)
- **FileValidator**: extrae FolderInfo del origin (parentFolder + childFolder)

## Configuracion

### Estructura de Configuracion

La configuracion se centraliza en:
- `ProcessorConfig.java`: agrupa settings de todos los procesadores (`soap` y `s3`)
- `ProcessorSettings.java`: record inmutable con los settings de un procesador (maxSize, allowedTypes, foldersToSkip, keywords, originPatternsToSend, etc.)
- `FileUploadProperties.java`: propiedades legacy de upload

### Variables de Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `app.processors.soap.max-size` | 10485760 | Tamano maximo para SOAP (bytes) |
| `app.processors.soap.allowed-types` | `pdf,txt,csv` | Tipos permitidos SOAP |
| `app.processors.soap.max-filename-length` | 255 | Longitud maxima de filename |
| `app.processors.soap.folders-to-skip` | `[/tmp,/transient]` | Carpetas a excluir |
| `app.processors.soap.origin-patterns-to-send` | `[incoming,documents]` | Patrones de origin |
| `app.processors.s3.max-size` | 52428800 | Tamano maximo para S3 (bytes) |
| `app.processors.s3.allowed-types` | `pdf,txt,csv,zip` | Tipos permitidos S3 |
| `app.processors.s3.max-filename-length` | 255 | Longitud maxima de filename |
| `app.processors.s3.folders-to-skip` | `[/tmp]` | Carpetas a excluir |
| `app.processors.s3.origin-patterns-to-send` | `[incoming,documents,archive]` | Patrones de origin |
| `app.soap.endpoint` | `${SOAP_ENDPOINT}` | Endpoint del servicio SOAP |
| `app.soap.timeout-seconds` | 30 | Timeout SOAP |
| `app.aws.s3.bucket-name` | `${AWS_BUCKET}` | Bucket S3 |
| `app.aws.s3.region` | `${AWS_REGION}` | Region AWS |
| `app.circuit-breaker.failure-rate-threshold` | 50 | Umbral de fallo % para CB |
| `app.circuit-breaker.wait-duration-in-open-state-millis` | 60000 | Tiempo en OPEN |
| `app.circuit-breaker.sliding-window-size` | 100 | Ventana de medicion |
| `app.shutdown.drain-timeout-seconds` | 20 | Tiempo max para drenar pipeline |

### Perfiles Spring

| Perfil | Implementacion | Uso |
|--------|---------------|-----|
| `soap` (default) | SoapDocumentProcessingUseCase | Envio via SOAP |
| `s3` | S3DocumentProcessingUseCase | Upload a AWS S3 |
| sin perfil | SoapDocumentProcessingUseCase | Default igual que soap |

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

### Metricas (Prometheus)

```bash
# Metricas en formato Prometheus
curl -s http://localhost:8080/actuator/prometheus
```

**Metricas clave disponibles:**

| Metrica | Tipo | Descripcion |
|---------|------|-------------|
| `documents_processed_total{status}` | Counter | Documentos procesados por estado |
| `documents_processing_duration_seconds` | Timer | Duracion del procesamiento |
| `product_load_total` | Counter | Productos cargados desde REST API |

### Logs Estructurados

El servicio utiliza MDC (Mapped Diagnostic Context) para logs correlacionados:

```properties
# Formato actual (console pattern)
%-5level [%thread] [%X{traceId}] %logger{36} - %msg%n
```

**Ejemplo de log:**
```
INFO [reactor-http-nio-2] [550e8400-e29b-41d4-a716-446655440000] c.e.f.handler.ProductHandler - Processing 3 documents
INFO [reactor-http-nio-2] [660e8400-e29b-41d4-a716-446655440001] c.e.f.usecase.SoapDocumentProcessingUseCase - correlationId=abc123 - Document SUCCESS
```

---

## Requisitos del Sistema

| Componente | Version | Notas |
|------------|---------|-------|
| JDK | 21+ | Requerido para Spring WebFlux 3.x |
| Gradle | 8.5+ | Wrapper incluido (`./gradlew`) |
| Docker | 24+ | Solo para ejecutar mocks |
| Base de datos | H2 (dev), PostgreSQL R2DBC (prod) | H2 en memoria para desarrollo |

## Resiliencia

### Circuit Breaker (Resilience4j)

El servicio tiene configuracion de Circuit Breaker con Resilience4j en `application.yml`:

```yaml
app:
  circuit-breaker:
    failure-rate-threshold: 50
    wait-duration-in-open-state-millis: 60000
    permitted-number-of-calls-in-half-open-state: 10
    sliding-window-size: 100
```

### Crash Recovery

Si el servicio cae mientras un documento estaba en procesamiento (`PROCESSING`):

1. Al iniciar, `DatabaseInitializer.resetProcessingProductDocuments()` ejecuta:
   ```sql
   UPDATE product_documents_to_process
   SET status = 'PENDING'
   WHERE status = 'PROCESSING';
   ```
2. Documentos atascados vuelven a `PENDING` y se procesan en el siguiente ciclo
3. `claimDocument()` garantiza que solo un pod procese cada documento

### Graceful Shutdown

`GracefulShutdownManager` maneja el drenado del pipeline antes del apagado:
- Configurable via `app.shutdown.drain-timeout-seconds` (default 20s)
- Coordinado con `server.shutdown: graceful` y `management.health.probes.enabled`

## Integracion SOAP

> **Nota**: El XSD Schema no esta incluido en el proyecto. El contrato SOAP esta definido implicitamente por las clases en `infrastructure/helpers/soap/xml/model/`:
> - `UploadFileRequest.java`
> - `UploadFileResponse.java`

### Estructura del Request SOAP

Los campos del request se mapean desde `DocumentSendRequest.java`:

| Campo | Tipo | Descripcion |
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

# Ver estado de operacion
curl -X GET "http://localhost:8080/api/v1/operations/{traceId}/status" \
  -H "Accept: application/json"

# Verificar health
curl -s http://localhost:8080/actuator/health | jq .

# Ver metricas
curl -s http://localhost:8080/actuator/metrics | jq .
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

## Mocks Disponibles y Validacion

### Mock REST de Productos (Java)
```bash
# Iniciar mock
./scripts/start-product-mock.sh

# Validar que esta corriendo
curl -s http://localhost:3001/api/products | jq 'length'
```

### Mock REST de Productos (Mockoon Desktop)
Importar `mockoon/document-rest-mock.json` en Mockoon Desktop.

**Validacion del contrato:**
```bash
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

### Mock S3 (LocalStack)
```bash
# Iniciar mock
./scripts/start-s3-mock.sh

# Validar que LocalStack esta corriendo
curl -s http://localhost:4566/_localstack/health | jq .

# Listar buckets
aws --endpoint-url=http://localhost:4566 s3 ls
```

## Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | Endpoint de API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Endpoint del servicio SOAP |
| `AWS_ENDPOINT` | `` | Endpoint S3 (LocalStack: `http://localhost:4566`) |
| `AWS_BUCKET` | `documents-bucket` | Bucket S3 para uploads |
| `AWS_REGION` | `us-east-1` | Region AWS |
| `AWS_ACCESS_KEY` | `` | Access key AWS |
| `AWS_SECRET_KEY` | `` | Secret key AWS |
| `H2_CONSOLE_ENABLED` | `false` | Habilitar consola H2 |
