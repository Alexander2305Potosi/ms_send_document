# File Processor Service

Microservicio reactivo basado en Spring WebFlux + R2DBC que obtiene productos con sus documentos asociados desde una API REST externa, los persiste en base de datos H2 (desarrollo) o PostgreSQL (produccion) y los envia a un servicio SOAP externo o AWS S3.

---

## Tabla de Contenidos

1. [Arquitectura (Clean Architecture)](#arquitectura-clean-architecture)
2. [API Endpoints](#api-endpoints)
3. [Flujo de Datos](#flujo-de-datos)
4. [Base de Datos](#base-de-datos)
5. [Descompresion de archivos ZIP](#descompresion-de-archivos-zip)
6. [Estados de Productos (ProductState)](#estados-de-productos-productstate)
7. [Validacion de Documentos (RulesBussinesService)](#validacion-de-documentos-rulesbussinesservice)
8. [Escenarios de Procesamiento](#escenarios-de-procesamiento)
9. [Codigos de Error](#codigos-de-error)
10. [Trazabilidad de Envios](#trazabilidad-de-envios)
11. [Template Method Pattern](#template-method-pattern)
12. [Perfiles de Ejecucion](#perfiles-de-ejecucion)
13. [Variables de Entorno](#variables-de-entorno)
14. [Compilacion y Ejecucion](#compilacion-y-ejecucion)
15. [Ejemplos de curl](#ejemplos-de-curl)
16. [Excepciones](#excepciones)
17. [Testing](#testing)

---

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas estrictamente separadas. La capa de dominio es Java puro sin dependencias de frameworks. La capa de infraestructura contiene los adaptadores concretos (R2DBC, REST, SOAP, S3). La comunicacion entre capas se realiza a traves de puertos (interfaces en `port/out`).

```
com.example.fileprocessor/
├── Application.java                              # @SpringBootApplication (excluye WebMvc)
│
├── domain/                                       # Capa de dominio
│   ├── entity/
│   │   ├── Product.java                          # Record: producto con lista de documentos
│   │   ├── ProductDocument.java                  # Record: documento (puede ser ZIP)
│   │   ├── ProductState.java                     # Constantes: PENDING, IN_PROGRESS, PROCESSED, FAILED
│   │   ├── FileUploadRequest.java                # Request para upload a gateway (SOAP/S3)
│   │   ├── FileUploadResult.java                 # Resultado de upload con status, errorCode, attemptCount
│   │   ├── DocumentStatus.java                   # Enum: SUCCESS, FAILURE
│   │   ├── DocumentHistory.java                  # Record: trazabilidad de envio a BD
│   │   └── ExternalServiceResponse.java          # Respuesta generica de servicio externo
│   ├── usecase/
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementacion SOAP
│   │   ├── S3DocumentProcessingUseCase.java         # Implementacion S3
│   │   ├── SyncProductsUseCase.java                 # Sincroniza productos a BD
│   │   └── ProcessingResultCodes.java               # Constantes de codigos de error
│   ├── service/
│   │   └── RulesBussinesService.java              # Validacion: tamano, patron filename
│   ├── util/
│   │   ├── ZipDecompressor.java                   # Descompresion de ZIP con inferencia de contentType
│   │   └── Base64Utils.java                       # Encoding/decoding seguro de Base64
│   ├── port/out/
│   │   ├── ProductRestGateway.java                # Puerto: API REST externa de productos
│   │   ├── ProductRepository.java                 # Puerto: persistencia y consulta de productos
│   │   ├── DocumentHistoryRepository.java         # Puerto: trazabilidad de envios
│   │   ├── RulesBussinesGateway.java              # Puerto: validacion de documentos
│   │   ├── S3Gateway.java                         # Puerto: envio a S3
│   │   └── SoapGateway.java                       # Puerto: envio a SOAP
│   └── exception/
│       ├── DomainException.java                   # Base abstracta (RuntimeException + errorCode)
│       ├── FileValidationException.java           # Error de validacion de archivo
│       ├── InvalidBase64Exception.java            # Error de decodificacion Base64
│       └── ProcessingException.java              # Error de procesamiento (traceId + documentId)
│
├── application/                                   # Capa de aplicacion
│   └── service/config/
│       └── DomainConfig.java                      # @Configuration: beans de casos de uso
│
└── infrastructure/                                # Capa de infraestructura
    ├── config/
    │   └── ProcessorsProperties.java              # @ConfigurationProperties("app.processors")
    ├── drivenadapters/
    │   ├── r2dbc/                                 # Adaptadores reactivos R2DBC
    │   │   ├── ProductR2dbcAdapter.java            # Implementa ProductRepository
    │   │   ├── DocumentHistoryR2dbcAdapter.java    # Implementa DocumentHistoryRepository
    │   │   ├── entity/
    │   │   │   ├── ProductEntity.java              # @Entity @Table("productos")
    │   │   │   └── DocumentHistoryEntity.java      # @Entity @Table("historico_documentos")
    │   │   ├── mapper/
    │   │   │   ├── ProductMapper.java              # Product <-> ProductEntity
    │   │   │   └── DocumentHistoryMapper.java      # DocumentHistory <-> DocumentHistoryEntity
    │   │   └── repository/
    │   │       ├── ProductRepository.java          # R2dbcRepository<ProductEntity, Long>
    │   │       └── DocumentHistoryRepository.java  # R2dbcRepository<DocumentHistoryEntity, Long>
    │   ├── restclient/
    │   │   ├── ProductRestGatewayAdapter.java      # WebClient a API REST externa
    │   │   └── dto/
    │   │       ├── ProductResponse.java            # DTO JSON de producto
    │   │       └── ProductDocumentResponse.java    # DTO JSON de documento (Base64)
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java             # Envio SOAP con reintentos + backoff
    │   │   ├── SoapErrorCodes.java                 # Constantes de error SOAP
    │   │   └── config/
    │   │       └── SoapProperties.java             # @ConfigurationProperties("app.soap")
    │   └── aws/
    │       ├── S3GatewayAdapter.java               # Envio S3 async con reintentos
    │       ├── S3ErrorCodes.java                   # Constantes de error S3
    │       └── config/
    │           ├── AwsConfig.java                   # Bean S3AsyncClient
    │           └── S3Properties.java                # @ConfigurationProperties("app.aws.s3")
    ├── entrypoints/rest/
    │   ├── ProductRoutes.java                      # Router function (WebFlux funcional)
    │   ├── handler/
    │   │   └── ProductHandler.java                 # Handler de endpoints REST
    │   ├── config/
    │   │   └── DocumentRestProperties.java         # @ConfigurationProperties("app.document-rest")
    │   └── constants/
    │       ├── RestApiPaths.java                   # Rutas de la API
    │       └── ApiConstants.java                   # Constantes (headers, parametros)
    └── helpers/soap/
        ├── SoapConstants.java                      # Namespaces SOAP, templates XML
        ├── mapper/
        │   └── SoapMapper.java                     # JAXB marshalling/unmarshalling + Base64
        └── xml/
            ├── SoapEnvelopeWrapper.java            # Envoltorio SOAP con parseo DOM seguro
            └── model/
                ├── UploadFileRequest.java           # @XmlRootElement para request SOAP
                └── UploadFileResponse.java          # @XmlRootElement para response SOAP
```

### Recursos

```
src/main/resources/
├── application.yml              # Configuracion base
├── application-dev.yml          # Perfil desarrollo (DEBUG, timeouts cortos)
├── application-prod.yml         # Perfil produccion (WARN, graceful shutdown)
├── schema.sql                   # DDL para H2 (desarrollo)
└── schema-postgresql.sql        # DDL para PostgreSQL (produccion)
```

---

## API Endpoints

### GET /api/v1/products

Procesa documentos pendientes de productos en la fecha actual desde base de datos. Los productos en estado PENDING se marcan IN_PROGRESS durante el procesamiento y al finalizar pasan a PROCESSED o FAILED.

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion. Si no se envia, se genera un UUID automatico.

**Query Parameters:**
- `processor`: `soap` (default) | `s3` — Selecciona el gateway de salida.

**Response:** `Content-Type: application/x-ndjson` (Server-Sent Events / NDJSON)
```json
{"correlationId":"corr-123","status":"SUCCESS","success":true,"processedAt":"2026-04-30T20:15:00Z","errorCode":null,"attemptCount":1}
{"correlationId":"corr-124","status":"FAILURE","success":false,"processedAt":"2026-04-30T20:15:01Z","errorCode":"GATEWAY_TIMEOUT","attemptCount":3}
```

**Errores:**
- `400 Bad Request` — Si `?processor=` tiene un valor no soportado.
- `503 Service Unavailable` — Si se solicita `?processor=s3` pero el perfil S3 no esta activo.

### POST /api/v1/products/sync

Sincroniza productos desde la API REST externa hacia la base de datos. Cada producto se persiste con `estado=PENDING` y `fecha_carga=now()`.

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion.

**Response:** HTTP 200 (fire-and-forget — la operacion se ejecuta asincronamente)
```json
{"status":"OK","message":"Products sync initiated"}
```

### GET /actuator/health

Health check. Expone health, info, metrics, loggers y prometheus.

---

## Flujo de Datos

### Flujo de Sincronizacion (POST /api/v1/products/sync)

```
1. Cliente                           2. REST API Externa
   POST /api/v1/products/sync  ──►   GET {productsPath}  (/api/products)
   ◄─────────────────────────────────
         [ProductResponse, ...]
               │
               ▼
3. SyncProductsUseCase.execute()
   ├── productRestGateway.getAllProducts()
   ├── Cada producto → Product(state=PENDING, loadDate=now())
   └── productRepository.save()
               │
               ▼
4. BD (productos)
```

### Flujo de Procesamiento (GET /api/v1/products)

```
1. Cliente
   GET /api/v1/products?processor=soap
        │
        ▼
2. ProductHandler.processPendingProducts()
   ├── Resuelve traceId (header message-id o UUID)
   ├── Selecciona processor (soap o s3)
   └── Escribe traceId en contexto reactivo
        │
        ▼
3. AbstractDocumentProcessingUseCase.executePendingDocuments()
        │
        ▼
4. ProductRepository.findByLoadDate(LocalDate.now())
        │  Filtra: fecha_carga=hoy AND estado=PENDING
        ▼
5. BD → Flux<Product>
        │
        ▼
6. Por cada producto: markProductInProgress(productId)
        │  Actualiza estado a IN_PROGRESS
        ▼
7. Por cada documento: processDocument(doc, productId)
   ├── ProductRestGateway.getDocument(productId, docId)
   │     └── GET {productDocumentsPath}/{docId}
   │     └── Decodifica Base64 via Base64Utils.decodeSafe()
   ├── ZipDecompressor.decompress()  [si isZip=true]
   │     └── Expande ZIP en archivos individuales
   ├── RulesBussinesGateway.validate(document)
   │     └── Valida tamano maximo y patron filename
   │     └── Documentos invalidos se omiten (Mono.empty())
   │     └── Si todos son invalidos → ProcessingException(INVALID_RESPONSE)
   ├── uploadDocument() → SoapGateway.send() o S3Gateway.send()
   │     └── Con reintentos automaticos + backoff
   └── saveHistory(doc, productId, result)
         └── INSERT en historico_documentos
        │
        ▼
8. Por cada producto: markProductFinished(productId, results)
        │  Si todos SUCCESS → PROCESSED, si algun FAILURE → FAILED
        ▼
9. Flux<FileUploadResult> → NDJSON stream al cliente
```

---

## Base de Datos

El servicio utiliza **R2DBC** (Reactive Relational Database Connectivity) para acceso no bloqueante a base de datos.

### Desarrollo: H2

En desarrollo se usa H2 en memoria con `r2dbc-h2`. El esquema se crea via `schema.sql`.

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///fileprocessor;DB_CLOSE_DELAY=-1
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

### Produccion: PostgreSQL

En produccion se usa PostgreSQL con `r2dbc-postgresql`. Para configurarlo, agregar la dependencia y ajustar la configuracion:

```kotlin
// build.gradle.kts
runtimeOnly("org.postgresql:r2dbc-postgresql")
runtimeOnly("org.postgresql:postgresql")
```

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fileprocessor}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
```

El script DDL para PostgreSQL esta en `src/main/resources/schema-postgresql.sql`. Para aplicarlo:

```bash
psql -h <host> -U <user> -d <database> -f schema-postgresql.sql
```

### Tabla: productos

Almacena los productos sincronizados desde la API REST externa.

| Columna | Tipo | Descripcion |
|--------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador unico auto-generado |
| `id_producto` | VARCHAR(255) | Identificador del producto en el sistema externo |
| `nombre` | VARCHAR(500) | Nombre del producto |
| `fecha_carga` | TIMESTAMP | Fecha de carga para filtrado diario |
| `estado` | VARCHAR(20) | PENDING / IN_PROGRESS / PROCESSED / FAILED |
| `mensaje_error` | VARCHAR(2000) | Mensaje de error si hubo fallo |

### Tabla: historico_documentos

Almacena la trazabilidad completa de cada intento de envio de documentos.

| Columna | Tipo | Descripcion |
|--------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador unico auto-generado |
| `id_producto` | VARCHAR(255) | Referencia al producto |
| `id_documento` | VARCHAR(500) | ID del documento (original o ruta si vino de ZIP) |
| `nombre_archivo` | VARCHAR(500) | Nombre del archivo enviado |
| `nombre_comprimido` | VARCHAR(500) | Si es ZIP, nombre del archivo ZIP original |
| `estado` | VARCHAR(20) | SUCCESS / FAILURE |
| `codigo_error` | VARCHAR(100) | Codigo de error categorizado |
| `razon_fallo` | VARCHAR(2000) | Mensaje de error legible |
| `numero_intentos` | INT | Numero de intentos realizados (default: 1) |
| `fecha_envio` | TIMESTAMP | Timestamp de envio exitoso (nullable) |
| `fecha_fallo` | TIMESTAMP | Timestamp de fallo (nullable) |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

### Indices

```sql
-- historico_documentos
CREATE INDEX idx_hist_producto   ON historico_documentos (id_producto);
CREATE INDEX idx_hist_estado     ON historico_documentos (estado);
CREATE INDEX idx_hist_created    ON historico_documentos (fecha_creacion DESC);

-- productos
CREATE INDEX idx_prod_estado       ON productos (estado);
CREATE INDEX idx_prod_fecha_carga  ON productos (fecha_carga);
CREATE INDEX idx_prod_producto_id  ON productos (id_producto);
CREATE INDEX idx_prod_carga_estado ON productos (fecha_carga, estado);
```

### Acceso a Consola H2 (solo desarrollo)

- **URL:** `http://localhost:8080/h2-console`
- **JDBC URL:** `jdbc:h2:mem:fileprocessor`
- **User:** `sa`
- **Password:** (vacio)

### Consultas SQL utiles

```sql
-- Ver todos los envios
SELECT * FROM historico_documentos ORDER BY fecha_creacion DESC;

-- Ver envios de un producto especifico
SELECT * FROM historico_documentos WHERE id_producto = 'prod-123';

-- Ver solo fallos
SELECT * FROM historico_documentos WHERE estado = 'FAILURE';

-- Ver productos pendientes
SELECT * FROM productos WHERE estado = 'PENDING';

-- Ver productos en progreso
SELECT * FROM productos WHERE estado = 'IN_PROGRESS';

-- Contar envios por estado
SELECT estado, COUNT(*) FROM historico_documentos GROUP BY estado;

-- Productos con fallos hoy
SELECT p.id_producto, p.estado, h.codigo_error, h.razon_fallo
FROM productos p
JOIN historico_documentos h ON p.id_producto = h.id_producto
WHERE h.estado = 'FAILURE' AND p.fecha_carga >= CURRENT_DATE();
```

---

## Descompresion de archivos ZIP

`ZipDecompressor.decompress()` expande documentos ZIP. El `documentId` resultante incluye la ruta: `originalId/filename`. Cada archivo expandido se procesa y persiste independientemente.

### Comportamiento

| Escenario | Resultado |
|-----------|-----------|
| Documento normal (`isZip=false`) | Se procesa tal cual |
| Documento ZIP con N archivos | Se expande en N `ProductDocument` individuales |
| ZIP vacio | `Flux.empty()`, no produce documentos |
| ZIP corrupto | `ProcessingException` con errorCode `INVALID_ZIP` |

### Inferencia de contentType

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
  ├── test.pdf  →  documentId="doc-1/test.pdf", isZip=false, contentType=application/pdf
  └── data.csv  →  documentId="doc-1/data.csv", isZip=false, contentType=text/csv
```

Cada archivo expandido genera su propio registro en `historico_documentos`.

---

## Estados de Productos (ProductState)

```java
public final class ProductState {
    public static final String PENDING = "PENDING";         // Sincronizado, esperando procesamiento
    public static final String IN_PROGRESS = "IN_PROGRESS"; // En procesamiento actual
    public static final String PROCESSED = "PROCESSED";     // Todos los documentos enviados exitosamente
    public static final String FAILED = "FAILED";           // Al menos un documento fallo
}
```

### Transiciones de Estado

```
               sync
                 │
                 ▼
            [PENDING]
                 │
                 │  executePendingDocuments()
                 ▼
          [IN_PROGRESS]
                 │
        ┌────────┴────────┐
        ▼                 ▼
  [PROCESSED]        [FAILED]
  (todos docs OK)    (al menos un doc fallo)
```

- **PENDING → IN_PROGRESS**: `markProductInProgress()` al iniciar el procesamiento del producto.
- **IN_PROGRESS → PROCESSED**: `markProductFinished()` cuando todos los documentos resultan SUCCESS.
- **IN_PROGRESS → FAILED**: `markProductFinished()` cuando al menos un documento resulta FAILURE.

---

## Validacion de Documentos (RulesBussinesService)

`RulesBussinesService` valida cada documento antes de enviarlo. Los documentos que no pasan la validacion se ignoran silenciosamente (retornan `Mono.empty()`, no generan error, no aparecen en el stream, no se registran en trazabilidad). Si todos los documentos de un producto fallan la validacion, se emite una `ProcessingException` con codigo `INVALID_RESPONSE` y se registra en la trazabilidad como fallo.

| Regla | Configuracion | Comportamiento |
|-------|--------------|----------------|
| **Tamano maximo** | `app.processors.{soap,s3}.max-file-size-bytes` | Omite si `doc.size() > max` |
| **Patron filename** | `app.processors.{soap,s3}.filename-pattern` | Omite si filename no coincide con la regex |

### Configuracion por defecto

```yaml
app:
  processors:
    s3:
      max-file-size-bytes: 52428800        # 50 MB
      filename-pattern: ".*\\.(pdf|csv)$"
    soap:
      max-file-size-bytes: 10485760        # 10 MB
      filename-pattern: ".*\\.(pdf|docx|txt)$"
```

### Reintentos

Ambos gateways (SOAP y S3) implementan reintentos automaticos con backoff.

**SOAP Gateway:**
- Reintentos: `app.soap.retry-attempts` (default: 3)
- Backoff: 500ms fijo
- Condiciones reintentables: HTTP 503, 502, 504, 429, `TimeoutException`, `ConnectException`

**S3 Gateway:**
- Reintentos: `app.aws.s3.retry-attempts` (default: 3)
- Backoff: `app.aws.s3.retry-backoff-millis` (default: 500ms)
- Condiciones reintentables: `TimeoutException`, `SdkException`

El campo `attemptCount` en `FileUploadResult` y `numero_intentos` en `historico_documentos` reflejan el numero de intentos realizados.

---

## Escenarios de Procesamiento

### 1. Exitoso
```
Producto PENDING → IN_PROGRESS
validate() → pasa
uploadDocument() → SUCCESS
saveHistory() → historico_documentos (estado=SUCCESS, fecha_envio=now)
Producto → PROCESSED
Stream: {"success":true, "status":"SUCCESS"}
```

### 2. Validacion Fallida (documento ignorado)
```
validate() → tamano excede limite O filename no coincide
return Mono.empty() → documento no se procesa
Sin registro en trazabilidad, sin entrada en stream
Si todos los docs del producto son invalidos → ProcessingException(INVALID_RESPONSE)
```

### 3. Error en Gateway
```
uploadDocument() → exception (con reintentos agotados)
saveFailedHistory() → construye FileUploadResult(status=FAILURE)
saveHistory() → historico_documentos (estado=FAILURE, codigo_error, fecha_fallo=now)
Producto → FAILED
Stream: {"success":false, "status":"FAILURE", "errorCode":"GATEWAY_TIMEOUT", "attemptCount":3}
```

### 4. Documento ZIP
```
isZip=true → ZipDecompressor.decompress()
           → Flux de archivos individuales
           → cada archivo pasa por validate(), uploadDocument(), saveHistory()
           → cada archivo genera su propio registro en historico_documentos
```

### 5. Error en Descompresion ZIP
```
ZipDecompressor.decompress() → ProcessingException(INVALID_ZIP)
onErrorResume() en saveFailedHistory() → status=FAILURE
saveHistory() → historico_documentos (codigo_error=INVALID_ZIP)
Stream: {"success":false, "status":"FAILURE", "errorCode":"INVALID_ZIP"}
```

### 6. Error de Base64
```
Base64Utils.decodeSafe() → InvalidBase64Exception(INVALID_BASE64)
saveFailedHistory() → status=FAILURE
saveHistory() → historico_documentos (codigo_error=INVALID_BASE64)
Stream: {"success":false, "status":"FAILURE", "errorCode":"INVALID_BASE64"}
```

---

## Codigos de Error

### Errores de Dominio (ProcessingResultCodes)

Definidos en `domain/usecase/ProcessingResultCodes.java`:

| Codigo | Descripcion |
|--------|-------------|
| `EMPTY_CONTENT` | Documento sin contenido |
| `INVALID_BASE64` | Fallo al decodificar Base64 |
| `INVALID_RESPONSE` | Respuesta SOAP invalida o malformada |
| `INVALID_ZIP` | Archivo ZIP corrupto o invalido |
| `UNKNOWN_ERROR` | Error no categorizado |

### Errores de Gateway SOAP (SoapErrorCodes)

Definidos en `infrastructure/drivenadapters/soap/SoapErrorCodes.java`:

| Codigo | Disparador |
|--------|-----------|
| `GATEWAY_TIMEOUT` | `TimeoutException` en HTTP call |
| `BAD_GATEWAY` | `WebClientResponseException` (respuesta HTTP no exitosa) |
| `SERVICE_UNAVAILABLE` | `ConnectException` |
| `UNKNOWN_ERROR` | `IOException` u otras excepciones no previstas |

### Errores de Gateway S3 (S3ErrorCodes)

Definidos en `infrastructure/drivenadapters/aws/S3ErrorCodes.java`:

| Codigo | Disparador |
|--------|-----------|
| `GATEWAY_TIMEOUT` | `TimeoutException` en operacion S3 |
| `BAD_GATEWAY` | Error generico de gateway |
| `CLIENT_ERROR` | Error 4xx generico |
| `ACCESS_DENIED` | S3Exception con status 403 |
| `NOT_FOUND` | S3Exception con status 404 |
| `SERVICE_UNAVAILABLE` | S3Exception 503, SdkException, throttling |
| `UNKNOWN_ERROR` | Cualquier otra excepcion no mapeada |

---

## Trazabilidad de Envios

Cada documento procesado deja un registro en `historico_documentos` a traves del adaptador `DocumentHistoryR2dbcAdapter` (implementa `DocumentHistoryRepository`).

### Mapeo de FileUploadResult a DocumentHistory

```
FileUploadResult                    DocumentHistory
──────────────                      ───────────────
status=SUCCESS               →      status = "SUCCESS"
success=true                 →      sentAt = now()
                                    failedAt = null
correlationId                →      (no se persiste directamente)
errorCode                    →      errorCode
message                      →      failureReason
attemptCount                 →      attemptCount
status=FAILURE               →      status = "FAILURE"
success=false                →      sentAt = null
                                    failedAt = now()
```

### Flujo de Persistencia

```
uploadDocument() → FileUploadResult
        │
        ▼
saveHistory(doc, productId, result)
        │
        ▼
new DocumentHistory(
    null,                              // id (auto-generado)
    productId,                         // productId
    doc.documentId(),                  // documentId (ej: "doc-1/test.pdf" si es de ZIP)
    doc.filename(),                    // filename
    doc.isZip() ? doc.filename() : null, // compressedFilename
    isSuccess ? "SUCCESS" : "FAILURE", // status
    result.getErrorCode(),             // errorCode
    result.getMessage(),               // failureReason
    result.getAttemptCount(),          // attemptCount
    isSuccess ? now : null,            // sentAt
    !isSuccess ? now : null,           // failedAt
    now                                // createdAt
)
        │
        ▼
historyRepository.save(record)
        │
        ▼
DocumentHistoryR2dbcAdapter.save()
        │
        ▼
DocumentHistoryMapper.toEntity(record) → DocumentHistoryEntity
        │
        ▼
repository.save(entity) → INSERT en historico_documentos
```

---

## Template Method Pattern

El patron se implementa en `AbstractDocumentProcessingUseCase`:

```
AbstractDocumentProcessingUseCase
│
├── executePendingDocuments()           ← FINAL (template method)
│   ├── productRepository.findByLoadDate()    → BD (PENDING, hoy)
│   ├── markProductInProgress()              → estado = IN_PROGRESS
│   ├── processDocument(doc, productId)
│   │   ├── productRestGateway.getDocument() → REST externa
│   │   ├── decompressIfNeeded()             → ZipDecompressor
│   │   ├── documentValidator.validate()     → RulesBussinesService
│   │   ├── uploadDocument()                 → ABSTRACT
│   │   └── saveHistory()                    → BD (historico_documentos)
│   └── markProductFinished()               → PROCESSED o FAILED
│
├── uploadDocument()                     ← ABSTRACT
│   └── buildFileUploadRequest()         ← helper protegido
│
├── handleUploadError()                  ← helper protegido (reusable por subclases)
│
├── SoapDocumentProcessingUseCase
│   └── uploadDocument() → SoapGateway.send()
│
└── S3DocumentProcessingUseCase
    └── uploadDocument() → S3Gateway.send()
```

### Subclases

**SoapDocumentProcessingUseCase** — Envia documentos via SOAP. Bean definido en `DomainConfig` con validador configurado desde `properties.soap()`.

**S3DocumentProcessingUseCase** — Envia documentos via S3. Bean condicional (`@ConditionalOnBean(S3Gateway.class)`) definido en `DomainConfig` con validador desde `properties.s3()`. Se usa `ObjectProvider` en el handler para manejar su disponibilidad opcional.

---

## Perfiles de Ejecucion

### Perfil default (SOAP)

Activo sin argumentos adicionales. Usa `SoapGatewayAdapter` para envio de documentos.

```bash
./gradlew bootRun
```

### Perfil S3

Activado con `-Ps3`. Habilita `S3GatewayAdapter` y `AwsConfig`. Usa LocalStack (`localhost:4566`) con path-style access por defecto.

```bash
./gradlew bootRun -Ps3
```

Configuracion del perfil S3 en `application.yml`:
```yaml
spring:
  config:
    activate:
      on-profile: s3
app:
  aws:
    s3:
      endpoint: http://localhost:4566
      path-style-access: true
```

### Perfil dev

Sobrescribe configuraciones para desarrollo local. Timeouts cortos y logging DEBUG.

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

```yaml
# application-dev.yml
logging:
  level:
    com.example.fileprocessor: DEBUG
    org.springframework.web.reactive: DEBUG
    reactor.netty: DEBUG
app:
  soap:
    endpoint: "http://localhost:9000/soap/fileservice"
    timeout-seconds: 5
    retry-attempts: 1
  document-rest:
    endpoint: "http://localhost:3001"
    timeout-seconds: 5
```

### Perfil prod

Optimizado para produccion. Logging WARN, graceful shutdown de 30s.

```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

```yaml
# application-prod.yml
logging:
  level:
    com.example.fileprocessor: WARN
    org.springframework.web.reactive: WARN
server:
  shutdown: graceful
  lifecycle:
    timeout-per-shutdown-phase: 30s
app:
  soap:
    timeout-seconds: 15
    retry-attempts: 2
```

---

## Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | URL base de la API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Endpoint del servicio SOAP |
| `AWS_BUCKET` | `documents-bucket` | Nombre del bucket S3 |
| `AWS_REGION` | `us-east-1` | Region AWS |
| `AWS_ACCESS_KEY` | (vacio) | AWS Access Key (opcional, usa DefaultCredentialsProvider si no se setea) |
| `AWS_SECRET_KEY` | (vacio) | AWS Secret Key (opcional) |
| `DB_HOST` | `localhost` | Host de PostgreSQL (solo prod) |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `fileprocessor` | Nombre de base de datos PostgreSQL |
| `DB_USER` | `postgres` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `postgres` | Password de PostgreSQL |

---

## Compilacion y Ejecucion

### Requisitos

- Java 21+
- Gradle 8.12+ (wrapper incluido)

### Comandos

```bash
# Compilar
./gradlew build

# Solo compilar sin tests
./gradlew assemble

# Ejecutar tests unitarios
./gradlew test

# Ejecutar tests de mutacion (PiTest, umbral 60%)
./gradlew pitest

# Reporte de cobertura (JaCoCo, umbral 75%)
./gradlew jacocoTestReport

# Ejecutar (perfil default: SOAP)
./gradlew bootRun

# Ejecutar con perfil S3
./gradlew bootRun -Ps3

# Ejecutar con perfil dev
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## Ejemplos de curl

```bash
# Sincronizar productos desde API REST a BD
curl -X POST http://localhost:8080/api/v1/products/sync \
  -H "message-id: my-trace-123"

# Procesar documentos pendientes via SOAP
curl "http://localhost:8080/api/v1/products?processor=soap" \
  -H "message-id: my-trace-456"

# Procesar documentos pendientes via S3
curl "http://localhost:8080/api/v1/products?processor=s3" \
  -H "message-id: my-trace-789"

# Health check
curl -s http://localhost:8080/actuator/health | jq .

# Metricas Prometheus
curl -s http://localhost:8080/actuator/prometheus
```

---

## Excepciones

### Jerarquia

```
RuntimeException
 └── DomainException (abstract)
      ├── errorCode: String
      ├── FileValidationException    → error de validacion de archivo
      ├── InvalidBase64Exception     → error de decodificacion Base64
      └── ProcessingException        → error de procesamiento
           ├── traceId: String
           ├── documentId: String
           └── metodos factory:
               - withTraceId(message, errorCode, traceId)
               - withTraceId(message, errorCode, traceId, cause)
               - fromContext(ctx, message, errorCode)
               - fromContext(ctx, message, errorCode, documentId)
```

### ProcessingException

Los metodos factory extraen automaticamente el `traceId` del contexto reactivo via `ContextView`. La excepcion formatea el mensaje como:

```
{mensaje} [traceId={traceId}]
```

`HEADER_TRACE_ID = "message-id"` es la clave usada para leer/escribir el traceId en el contexto reactivo.

---

## Testing

### Estructura de tests

Los tests siguen la misma estructura de paquetes que `src/main`, bajo `src/test/java/com/example/fileprocessor/`.

### Frameworks

- **JUnit 5** + **Mockito** + **Reactor Test** (`StepVerifier`)
- **MockWebServer** (OkHttp) para simular API REST externa en tests de integracion
- **PiTest** para mutation testing (umbral de mutantes matados: 60%)
- **JaCoCo** para cobertura de codigo (umbral: 75%)

### Ejecutar tests

```bash
# Unit tests
./gradlew test

# Mutation tests
./gradlew pitest

# Coverage report
./gradlew jacocoTestReport
```

---

## Stack Tecnologico

| Componente | Tecnologia |
|-----------|-----------|
| **Framework** | Spring Boot 3.3.5 + WebFlux |
| **Lenguaje** | Java 21 |
| **Build** | Gradle 8.12.1 (Kotlin DSL) |
| **Base de datos (dev)** | H2 (embebida) |
| **Base de datos (prod)** | PostgreSQL |
| **Acceso a datos** | R2DBC + Spring Data R2DBC |
| **API REST externa** | WebClient (no bloqueante) |
| **Gateway SOAP** | WebClient + JAXB + DOM |
| **Gateway S3** | AWS SDK S3 Async (Netty) |
| **Observabilidad** | Micrometer Tracing (Brave) + Prometheus |
| **Validacion SOAP** | JAXB (jakarta.xml.bind) + DOM Parser seguro |
| **Testing** | JUnit 5, Mockito, Reactor Test, MockWebServer |
| **Calidad** | JaCoCo, PiTest (mutation testing) |
