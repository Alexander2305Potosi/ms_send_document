# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa, los persiste en base de datos H2 y los envía a un servicio SOAP externo o AWS S3.

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
│   │   └── ExternalServiceResponse.java  # Respuesta genérica de servicio externo
│   ├── usecase/
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementación SOAP
│   │   ├── S3DocumentProcessingUseCase.java         # Implementación S3
│   │   └── SyncProductsUseCase.java                 # Sincroniza productos a H2
│   ├── service/
│   │   └── RulesBussinesService.java    # Validación de documentos (tamaño, patrón filename)
│   ├── util/
│   │   └── ZipDecompressor.java       # Descompresión de archivos ZIP
│   ├── port/out/
│   │   ├── ProductRestGateway.java       # Puerto REST productos (origen externo)
│   │   ├── ProductDbGateway.java         # Puerto BD local (H2)
│   │   ├── ProductPersistenceGateway.java # Puerto para persistir productos
│   │   ├── RulesBussinesGateway.java     # Puerto de validación
│   │   ├── S3Gateway.java                 # Puerto S3
│   │   └── SoapGateway.java              # Puerto SOAP
│   └── exception/
│       ├── DomainException.java
│       ├── FileValidationException.java
│       └── ProcessingException.java
│
├── application/                         # Configuración de aplicación
│   └── service/config/
│       └── DomainConfig.java           # Beans de casos de uso
│
└── infrastructure/                    # Capa de infraestructura
    ├── drivenadapters/
    │   ├── jpa/                       # Adaptadores JPA para H2
    │   │   ├── ProductPersistenceAdapter.java
    │   │   ├── ProductDbAdapter.java
    │   │   ├── entity/PendingProductEntity.java
    │   │   └── repository/PendingProductRepository.java
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
- `message-id`: (opcional) Trace ID para correlación

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
- `message-id`: (opcional) Trace ID para correlación

**Response:** HTTP 200 (async fire-and-forget)
```json
{"status":"OK","message":"Products sync initiated"}
```

### GET /actuator/health

Health check de la aplicación.

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
5. H2 (pending_products)
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
        │ Valida tamaño y patrón filename
        ▼
9. uploadDocument() → SoapGateway.send() o S3Gateway.send()
        │
        ▼
10. FileUploadResult stream → Cliente (NDJSON)
```

---

## Descompresion de archivos ZIP

Cuando un `ProductDocument` tiene `isZip=true`, su contenido se descomprime y cada archivo individual se procesa separadamente.

### Comportamiento

| Escenario | Resultado |
|----------|-----------|
| Documento normal (`isZip=false`) | Se procesa tal cual |
| Documento ZIP con 3 archivos | Se expande en 3 `ProductDocument` individuales |
| ZIP vacio | Se loguea warning, no produce documentos |
| ZIP corrupto | `ProcessingException` con errorCode `INVALID_ZIP` |

### Inferencia de contentType

Los archivos descomprimidos inferen su `contentType` segun la extension:

| Extension | ContentType |
|----------|-------------|
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

Cada archivo pasa por validacion y upload independientemente.

---

## Base de Datos H2

### Tabla: pending_products

| Columna | Tipo | Descripcion |
|--------|------|-------------|
| `product_id` | VARCHAR (PK) | Identificador unico |
| `name` | VARCHAR | Nombre del producto |
| `load_date` | TIMESTAMP | Fecha de carga (filtrado diario) |
| `state` | VARCHAR | PENDING / PROCESSED / FAILED |
| `message_error` | VARCHAR | Mensaje de error si hubo fallo |
| `created_at` | TIMESTAMP | Fecha creacion registro |
| `updated_at` | TIMESTAMP | Fecha ultima actualizacion |

### Acceso a Consola H2
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:fileprocessor`
- User: `sa`
- Password: (vacío)

### Configuracion
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:fileprocessor;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
  h2:
    console:
      enabled: true
```

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
| **Tamaño maximo** | `processors.{soap,s3}.max-file-size-bytes` | Rechaza si `doc.size() > max` |
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
```

---

## Escenarios de Procesamiento

### 1. Exitoso
```
validate() → pasa
uploadDocument() → SUCCESS
Result: {success:true, status:SUCCESS}
```

### 2. Validacion Fallida (documento ignorado)
```
validate() → size excede limite O filename no matchea
return Mono.empty() → documento no se procesa
Result: (no aparece en stream)
```

### 3. Error en Gateway
```
uploadDocument() → exception
onErrorResume() → status=FAILURE
Result: {success:false, status:FAILURE, errorCode:UPLOAD_FAILED}
```

### 4. Documento ZIP
```
isZip=true → ZipDecompressor.decompress()
           → Flux de archivos individuales
           → cada archivo pasa por validate() y uploadDocument()
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

## Template Method Pattern

```
AbstractDocumentProcessingUseCase
│
├── executePendingDocuments()     ← FINAL (template method)
│   ├── productDbGateway.findByLoadDate()  ← H2
│   ├── productRestGateway.getDocument()   ← REST externa
│   ├── ZipDecompressor.decompress()        ← Descompresión ZIP
│   ├── rulesBussinesGateway.validate()    ← Validacion
│   └── uploadDocument()                 ← ABSTRACT (subclase implementa)
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
| `AWS_REGION` | `us-east-1` | Región AWS |
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
