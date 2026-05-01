# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa, los persiste en base de datos H2 y los envía a un servicio SOAP externo o AWS S3.

---

## Tabla de Contenidos

1. [Arquitectura](#arquitectura-clean-architecture)
2. [API Endpoints](#api-endpoints)
3. [Flujo de Datos](#flujo-de-datos)
4. [Base de Datos H2 con R2DBC](#base-de-datos-h2-con-r2dbc)
   - [Tabla: productos_pendientes](#tabla-productos_pendientes)
   - [Tabla: historico_documentos](#tabla-historico_documentos)
   - [Consultas SQL utiles](#consultas-sql-utiles)
5. [Descompresion de archivos ZIP](#descompresion-de-archivos-zip)
6. [Estados de Productos](#estados-de-productos-productstate)
7. [Validacion de Documentos](#validacion-de-documentos-rulesbussinesservice)
8. [Escenarios de Procesamiento](#escenarios-de-procesamiento)
9. [Codigos de Error](#codigos-de-error-processingresultcodes)
10. [Trazabilidad de Envios](#trazabilidad-de-envios)
11. [Template Method Pattern](#template-method-pattern)
12. [Variables de Entorno](#variables-de-entorno)
13. [Compilacion y Ejecucion](#compilacion-y-ejecucion)
14. [Ejemplos de curl](#ejemplos-de-curl)
15. [Excepciones](#excepciones)

---

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas claras:

```
com.example.fileprocessor/
├── domain/                              # Capa de dominio (puro Java, sin frameworks)
│   ├── entity/
│   │   ├── Product.java                  # Producto
│   │   ├── ProductDocument.java          # Documento dentro de Product
│   │   ├── ProductState.java             # Constantes de estado: PENDING, PROCESSED, FAILED
│   │   ├── FileUploadRequest.java        # Request para upload a gateway
│   │   ├── FileUploadResult.java         # Resultado de upload/procesamiento
│   │   ├── DocumentStatus.java           # Estados de documento (SUCCESS, FAILURE)
│   │   ├── DocumentTraceability.java     # Record para trazabilidad de envios
│   │   └── ExternalServiceResponse.java  # Respuesta generica de servicio externo
│   ├── usecase/
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementacion SOAP
│   │   ├── S3DocumentProcessingUseCase.java         # Implementacion S3
│   │   └── SyncProductsUseCase.java                 # Sincroniza productos a H2
│   ├── service/
│   │   └── RulesBussinesService.java    # Validacion de documentos (tamano, patron filename)
│   ├── util/
│   │   └── ZipDecompressor.java         # Descompresion de archivos ZIP
│   ├── port/out/
│   │   ├── ProductRestGateway.java       # Puerto REST productos (origen externo)
│   │   ├── ProductDbGateway.java         # Puerto BD local (H2)
│   │   ├── ProductPersistenceGateway.java # Puerto para persistir productos
│   │   ├── RulesBussinesGateway.java     # Puerto de validacion
│   │   ├── DocumentTraceabilityGateway.java # Puerto para trazabilidad
│   │   ├── S3Gateway.java                # Puerto S3
│   │   └── SoapGateway.java             # Puerto SOAP
│   └── exception/
│       ├── DomainException.java
│       ├── FileValidationException.java
│       └── ProcessingException.java
│
├── application/                         # Configuracion de aplicacion
│   └── service/config/
│       └── DomainConfig.java           # Beans de casos de uso
│
└── infrastructure/                    # Capa de infraestructura
    ├── drivenadapters/
    │   ├── r2dbc/                       # Adaptadores R2DBC para H2 (reactivo)
    │   │   ├── ProductPersistenceR2dbcAdapter.java
    │   │   ├── ProductDbR2dbcAdapter.java
    │   │   ├── DocumentTraceabilityR2dbcAdapter.java
    │   │   ├── PendingProductRowMapper.java
    │   │   └── DocumentTraceabilityRowMapper.java
    │   ├── restclient/
    │   │   └── ProductRestGatewayAdapter.java
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java
    │   │   └── config/SoapProperties.java
    │   └── aws/
    │       ├── S3GatewayAdapter.java
    │       └── config/S3Properties.java
    └── entrypoints/rest/
        ├── ProductRoutes.java
        ├── handler/ProductHandler.java
        └── constants/
            ├── RestApiPaths.java
            └── ApiConstants.java
```

---

## API Endpoints

### GET /api/v1/products

Procesa documentos pendientes de productos en la fecha actual desde base de datos H2.

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion

**Query Parameters:**
- `processor`: `soap` (default) | `s3`

**Response:** Server-Sent Events (NDJSON)
```json
{"correlationId":"corr-123","status":"SUCCESS","success":true,"processedAt":"2026-04-30T20:15:00Z"}
{"correlationId":"corr-124","status":"FAILURE","success":false,"errorCode":"UPLOAD_FAILED"}
```

### POST /api/v1/products/sync

Sincroniza productos desde API REST externa hacia base de datos H2 (estado PENDING).

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion

**Response:** HTTP 200 (async fire-and-forget)
```json
{"status":"OK","message":"Products sync initiated"}
```

### GET /actuator/health

Health check de la aplicacion.

---

## Flujo de Datos

### Flujo de Sincronizacion (POST /api/v1/products/sync)

```
1. Cliente                          2. REST API Externa
   POST /api/v1/products/sync  ──►  GET /api/products
   ◄────────────────────────────────
        [Product, Product, ...]
              │
              ▼
3. SyncProductsUseCase.execute()
        │
        ▼
4. ProductPersistenceGateway.save()
        │ (cada producto se persiste con state=PENDING)
        ▼
5. H2 (productos_pendientes)
```

### Flujo de Procesamiento (GET /api/v1/products)

```
1. Cliente
   GET /api/v1/products?processor=soap
        │
        ▼
2. AbstractDocumentProcessingUseCase.executePendingDocuments()
        │
        ▼
3. ProductDbGateway.findByLoadDate(LocalDate.now())
        │ Filtra: loadDate=hoy AND state=PENDING
        ▼
4. H2 → [Product prod-1, prod-2, ...]
        │
        ▼
5. Por cada Product → Flux.fromIterable(documents)
        │
        ▼
6. ProductRestGateway.getDocument(productId, docId)
        │ Obtiene documento completo (con contenido)
        ▼
7. ZipDecompressor.decompress() [si isZip=true]
        │ Expande ZIP en archivos individuales
        ▼
8. RulesBussinesGateway.validate(document)
        │ Valida tamano y patron filename
        ▼
9. uploadDocument() → SoapGateway.send() o S3Gateway.send()
        │
        ▼
10. DocumentTraceabilityGateway.save() [PERSISTE TRAZABILIDAD]
        │ Registra resultado en historico_documentos
        ▼
11. FileUploadResult stream → Cliente (NDJSON)
```

---

## Base de Datos H2 con R2DBC

El servicio utiliza **R2DBC** (Reactive Relational Database Connectivity) para acceso a la base de datos H2. Esto permite operaciones **completamente reactivas y no bloqueantes** dentro del pipeline de Spring WebFlux.

### Tabla: productos_pendientes

Tabla que almacena los productos sincronizados desde la API REST externa.

| Columna | Tipo | Descripcion |
|--------|------|-------------|
| `nombre_producto` | VARCHAR (PK) | Identificador unico del producto |
| `nombre` | VARCHAR | Nombre del producto |
| `fecha_carga` | TIMESTAMP | Fecha de carga (filtrado diario) |
| `estado` | VARCHAR | PENDING / PROCESSED / FAILED |
| `mensaje_error` | VARCHAR | Mensaje de error si hubo fallo |
| `fecha_creacion` | TIMESTAMP | Fecha creacion registro |
| `fecha_actualizacion` | TIMESTAMP | Fecha ultima actualizacion |

### Tabla: historico_documentos

Tabla que almacena la trazabilidad completa de cada intento de envio de documentos a las APIs externas (SOAP/S3).

| Columna | Tipo | Descripcion |
|--------|------|-------------|
| `id` | BIGINT (PK) | Identificador unico auto-generado |
| `nombre_producto` | VARCHAR | Referencia al producto (`productos_pendientes.nombre_producto`) |
| `nombre_documento` | VARCHAR | ID del documento (original si vino de ZIP) |
| `nombre_archivo` | VARCHAR | Nombre del archivo enviado |
| `nombre_comprimido` | VARCHAR | Si `isZip=true`, nombre del archivo ZIP original |
| `estado` | VARCHAR | SUCCESS / FAILURE / PENDING / RETRY |
| `codigo_error` | VARCHAR | Codigo de error (INVALID_BASE64, TIMEOUT, etc.) |
| `razon_fallo` | VARCHAR | Mensaje de error legible |
| `numero_intentos` | INT | Numero de intentos de envio (default: 1) |
| `fecha_envio` | TIMESTAMP | Timestamp de envio exitoso (nullable) |
| `fecha_fallo` | TIMESTAMP | Timestamp de fallo (nullable) |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

#### Estados posibles

| Estado | Significado |
|--------|-------------|
| `PENDING` | Documento en cola, aun no procesado |
| `SUCCESS` | Envio exitoso |
| `FAILURE` | Envio fallido (todos los intentos agotados) |
| `RETRY` | Reintentando actualmente |

#### Codigos de error persistidos

| Codigo | Categoria | Fase |
|--------|----------|------|
| `INVALID_BASE64` | Descarga | REST API |
| `INVALID_ZIP` | Descompresion | Pipeline |
| `EMPTY_CONTENT` | Validacion | Pipeline |
| `GATEWAY_TIMEOUT` | Envio | SOAP/S3 |
| `BAD_GATEWAY` | Envio | SOAP/S3 |
| `ACCESS_DENIED` | Envio | S3 |
| `NOT_FOUND` | Envio | S3 |
| `CLIENT_ERROR` | Envio | SOAP/S3 |
| `SERVICE_UNAVAILABLE` | Envio | SOAP/S3 |
| `UPLOAD_FAILED` | Envio | Pipeline |
| `UNKNOWN_ERROR` | Cualquiera | Todas |

### Acceso a Consola H2
- URL: `http://localhost:8080/h2-console`
- R2DBC URL: `r2dbc:h2:mem:///fileprocessor`
- User: `sa`
- Password: (vacio)

### Consultas SQL utiles

```sql
-- Ver todos los envios
SELECT * FROM historico_documentos ORDER BY fecha_creacion DESC;

-- Ver envios de un producto especifico
SELECT * FROM historico_documentos WHERE nombre_producto = 'prod-123';

-- Ver solo fallos
SELECT * FROM historico_documentos WHERE estado = 'FAILURE';

-- Ver productos pendientes
SELECT * FROM productos_pendientes WHERE estado = 'PENDING';
```

### Configuracion (R2DBC)
```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///fileprocessor;DB_CLOSE_DELAY=-1
    username: sa
    password:
  h2:
    console:
      enabled: true
```

---

## Descompresion de archivos ZIP

Cuando un `ProductDocument` tiene `isZip=true`, su contenido se descomprime y cada archivo individual se procesa separadamente.

### Comportamiento

| Escenario | Resultado |
|-----------|-----------|
| Documento normal (`isZip=false`) | Se procesa tal cual |
| Documento ZIP con 3 archivos | Se expande en 3 `ProductDocument` individuales |
| ZIP vacio | Se loguea warning, no produce documentos |
| ZIP corrupto | `ProcessingException` con errorCode `INVALID_ZIP` |

### Inferencia de contentType

Los archivos descomprimidos inferen su `contentType` segun la extension:

| Extension | ContentType |
|-----------|-------------|
| `.pdf` | `application/pdf` |
| `.csv` | `text/csv` |
| `.txt` | `text/plain` |
| `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| otra | `application/octet-stream` |

### Ejemplo

Un documento ZIP con `documentId=doc-1` y `filename=documents.zip` que contiene `test.pdf` y `data.csv`:

```
ZIP: doc-1/documents.zip (isZip=true)
  ├── test.pdf  →  doc-1/test.pdf (isZip=false, contentType=application/pdf)
  └── data.csv  →  doc-1/data.csv (isZip=false, contentType=text/csv)
```

Cada archivo pasa por validacion y upload independientemente. Cada archivo genera su propio registro en `historico_documentos`.

---

## Estados de Productos (ProductState)

```java
public final class ProductState {
    public static final String PENDING = "PENDING";     // Nuevo, esperando procesamiento
    public static final String PROCESSED = "PROCESSED"; // Procesado exitosamente
    public static final String FAILED = "FAILED";       // Fallo en procesamiento
}
```

### Transiciones
```
[PENDING] ────► [PROCESSED]  Cuando todos los documentos se envian exitosamente
    │
    ▼
[FAILED]      Cuando hay error irrecuperable
```

---

## Validacion de Documentos (RulesBussinesService)

`RulesBussinesService` valida cada documento antes de enviarlo:

| Regla | Config | Comportamiento |
|-------|--------|----------------|
| **Tamano maximo** | `processors.{soap,s3}.max-file-size-bytes` | Rechaza si `doc.size() > max` |
| **Patron filename** | `processors.{soap,s3}.filename-pattern` | Rechaza si filename no matchea regex |

### Configuracion
```yaml
app:
  processors:
    s3:
      max-file-size-bytes: 52428800        # 50MB
      filename-pattern: ".*\\.(pdf|csv)$"
    soap:
      max-file-size-bytes: 10485760       # 10MB
      filename-pattern: ".*\\.(pdf|docx|txt)$"
      retry-attempts: 3                   # Numero de reintentos en caso de fallo
```

### Reintentos SOAP

El gateway SOAP (`SoapGatewayAdapter`) implementa reintentos automaticos configurables:

| Condicion de Reintento | Codigos de Error HTTP |
|------------------------|----------------------|
| **503 Service Unavailable** | `SERVICE_UNAVAILABLE` |
| **502 Bad Gateway** | `BAD_GATEWAY` |
| **504 Gateway Timeout** | `GATEWAY_TIMEOUT` |
| **429 Too Many Requests** | `RATE_LIMITED` |
| **Timeout de conexion** | `GATEWAY_TIMEOUT` |
| **ConnectException** | `SERVICE_UNAVAILABLE` |

**Backoff:** 500ms entre intentos

**Trazabilidad:** El numero de intentos realizados se guarda en `historico_documentos.numero_intentos`.

---

## Escenarios de Procesamiento

### 1. Exitoso
```
validate() → pasa
uploadDocument() → SUCCESS
saveTraceability() → persiste SUCCESS en historico_documentos
Result: {success:true, status:SUCCESS}
```

### 2. Validacion Fallida (documento ignorado)
```
validate() → size excede limite O filename no matchea
return Mono.empty() → documento no se procesa
Result: (no aparece en stream, no hay registro en trazabilidad)
```

### 3. Error en Gateway
```
uploadDocument() → exception
onErrorResume() → status=FAILURE
saveTraceability() → persiste FAILURE con codigo_error
Result: {success:false, status:FAILURE, errorCode:UPLOAD_FAILED}
```

### 4. Documento ZIP
```
isZip=true → ZipDecompressor.decompress()
           → Flux de archivos individuales
           → cada archivo pasa por validate(), uploadDocument(), saveTraceability()
           → cada archivo genera su propio registro en historico_documentos
```

### 5. Error en Descompresion ZIP
```
ZipDecompressor.decompress() → ProcessingException(INVALID_ZIP)
           → onErrorResume() → status=FAILURE
saveTraceability() → persiste FAILURE con codigo_error=INVALID_ZIP
Result: {success:false, status:FAILURE, errorCode:INVALID_ZIP}
```

---

## Codigos de Error (ProcessingResultCodes)

| Codigo | Descripcion |
|--------|-------------|
| `EMPTY_CONTENT` | Documento sin contenido |
| `INVALID_BASE64` | Fallo al decodificar Base64 |
| `INVALID_RESPONSE` | Respuesta invalida del servicio externo |
| `INVALID_ZIP` | Archivo ZIP corrupto o invalido |
| `UPLOAD_FAILED` | Error en envio (SOAP/S3) |
| `UNKNOWN_ERROR` | Error no categorizado |

---

## Trazabilidad de Envios

La tabla `historico_documentos` permite:

- **Consultar historico de envios por producto**: `SELECT * FROM historico_documentos WHERE nombre_producto = 'prod-123'`
- **Conocer el estado final de cada documento**: La columna `estado` indica SUCCESS/FAILURE/PENDING
- **Saber razon de fallo**: Las columnas `codigo_error` y `razon_fallo` almacenan detalles
- **Identificar patrones de fallo**: Consultas sobre `fecha_fallo`, `codigo_error` revelan tendencias

### Flujo de Persistencia de Trazabilidad

```
uploadDocument() → FileUploadResult
        │
        ▼
saveTraceability(doc, productId, result)
        │
        ▼
DocumentTraceability record = new DocumentTraceability(
    null,                    // id (auto-generado)
    productId,               // nombre_producto
    doc.documentId(),        // nombre_documento
    doc.filename(),          // nombre_archivo
    doc.isZip() ? doc.filename() : null,  // nombre_comprimido
    isSuccess ? "SUCCESS" : "FAILURE",     // estado
    result.getErrorCode(),   // codigo_error
    result.getMessage(),     // razon_fallo
    1,                       // numero_intentos
    isSuccess ? now : null,  // fecha_envio
    !isSuccess ? now : null, // fecha_fallo
    now                      // fecha_creacion
)
        │
        ▼
traceabilityGateway.save(record)
        │
        ▼
DocumentTraceabilityR2dbcAdapter.save()
        │
        ▼
databaseClient.sql("INSERT INTO historico_documentos ...")
    .bind(...).then()  // Reactor non-blocking
```

---

## Template Method Pattern

```
AbstractDocumentProcessingUseCase
│
├── executePendingDocuments()     ← FINAL (template method)
│   ├── productDbGateway.findByLoadDate()  ← H2
│   ├── productRestGateway.getDocument()   ← REST externa
│   ├── ZipDecompressor.decompress()        ← Descompresion ZIP
│   ├── rulesBussinesGateway.validate()    ← Validacion
│   ├── uploadDocument()                   ← ABSTRACT (subclase implementa)
│   └── saveTraceability()                 ← Persistencia en H2
│
├── SoapDocumentProcessingUseCase
│   └── uploadDocument() → SoapGateway.send()
│
└── S3DocumentProcessingUseCase
    └── uploadDocument() → S3Gateway.send()
```

---

## Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Servicio SOAP |
| `AWS_BUCKET` | `documents-bucket` | Bucket S3 |
| `AWS_REGION` | `us-east-1` | Region AWS |
| `AWS_ACCESS_KEY` | `` | Access key |
| `AWS_SECRET_KEY` | `` | Secret key |

---

## Compilacion y Ejecucion

```bash
# Compilar y tests
./gradlew build

# Ejecutar tests
./gradlew test

# Ejecutar (perfil default = SOAP)
./gradlew bootRun

# Ejecutar con perfil S3
./gradlew bootRun -Ps3
```

---

## Ejemplos de curl

```bash
# Sincronizar productos a H2
curl -X POST http://localhost:8080/api/v1/products/sync

# Procesar con SOAP
curl "http://localhost:8080/api/v1/products?processor=soap"

# Procesar con S3
curl "http://localhost:8080/api/v1/products?processor=s3"

# Health check
curl -s http://localhost:8080/actuator/health
```

---

## Excepciones

### ProcessingException

Excepcion unificada para todos los errores de procesamiento. Incluye:
- `traceId`: Extraido automaticamente del contexto reactivo via `fromContext()`
- `documentId`: Opcional
- `errorCode`: Codigo de error (`ProcessingResultCodes`)

**Metodos factory:**
```java
ProcessingException.fromContext(ctx, message, errorCode)
ProcessingException.withTraceId(message, errorCode, traceId)
```

### DomainException

Base class para todas las excepciones de dominio.

### FileValidationException

Excepcion para errores de validacion de archivos.
