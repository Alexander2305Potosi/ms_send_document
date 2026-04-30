# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envía a un servicio SOAP externo o AWS S3.

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas claras:

```
com.example.fileprocessor/
├── domain/                          # Capa de dominio (puro Java, sin frameworks)
│   ├── entity/                      # Entidades de negocio
│   │   ├── Product.java               # Producto desde REST API
│   │   ├── ProductDocument.java       # Documento dentro de ProductInfo
│   │   ├── FileUploadRequest.java     # Request para upload a gateway
│   │   ├── FileUploadResult.java      # Resultado de upload/procesamiento
│   │   ├── ExternalServiceResponse.java # Respuesta genérica de servicio externo
│   │   ├── DocumentStatus.java        # Estados de documento (constantes)
│   │   └── ProductStatus.java         # Estados de producto
│   ├── usecase/                      # Casos de uso
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementación SOAP
│   │   ├── S3DocumentProcessingUseCase.java         # Implementación S3
│   │   └── ProcessingResultCodes.java               # Códigos de resultado
│   ├── service/
│   │   └── DocumentValidator.java   # Validación centralizada de documentos
│   ├── port/
│   │   ├── out/
│   │   │   ├── ProductRestGateway.java  # Puerto REST productos
│   │   │   ├── S3Gateway.java          # Puerto S3
│   │   │   ├── SoapGateway.java       # Puerto SOAP
│   │   │   └── BussinesParamsGateway.java # Puerto configuración
│   │   └── in/ (vacío - sin use cases de entrada REST directos)
│   └── exception/
│       ├── DomainException.java
│       ├── FileValidationException.java
│       └── ProcessingException.java   # Excepción unificada de procesamiento
│
├── application/                      # Configuración de aplicación
│   └── app-service/
│       └── config/
│           └── DomainConfig.java      # Beans de casos de uso
│
└── infrastructure/                   # Capa de infraestructura
    ├── entrypoints/
    │   └── rest/
    │       ├── ProductRoutes.java     # RouterFunction
    │       ├── handler/
    │       │   └── ProductHandler.java # Lógica de handlers
    │       └── constants/
    │           ├── RestApiPaths.java
    │           └── ApiConstants.java  # Constantes API (message-id, processor types)
    ├── drivenadapters/
    │   ├── rest-client/
    │   │   └── ProductRestGatewayAdapter.java
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java
    │   │   └── config/
    │   │       └── SoapProperties.java
    │   └── aws/
    │       ├── S3GatewayAdapter.java
    │       ├── config/
    │       │   ├── BussinesParams.java       # Enum de parámetros de negocio
    │       │   ├── ProcessingProperties.java # ConfigProperties para params
    │       │   └── ProcessingPropertiesConfig.java
    └── helpers/
        └── soap/
            ├── SoapConstants.java     # Constantes SOAP (namespaces, envelopes)
            ├── mapper/
            │   └── SoapMapper.java    # Mapeo XML ↔ objetos
            └── xml/
                └── SoapEnvelopeWrapper.java # Wrapper de envelope SOAP
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa (puro Java)
- **Domain** no contiene anotaciones Spring (`@Component`, etc.)
- **Infrastructure** y **Application** dependen de Domain
- **Application** expone los beans via `DomainConfig.java`

---

## Flujo de Procesamiento de Documentos

### Pipeline de Procesamiento

El pipeline usa **Reactor** (Project Reactor) con operaciones reactivas:

```
executePendingDocuments()
    │
    ▼
validate(doc) → ProductDocumentValidator.validate()
    │         ├── Validación de tamaño (maxFileSize de BussinesParams)
    │         └── Validación de patrón filename (regex de BussinesParams)
    ▼
uploadDocument(doc) → SOAP o S3 según el caso de uso
```

### Validación de Documentos (DocumentValidator)

`DocumentValidator` centraliza las validaciones de documentos:

1. **Validación de tamaño**: Verifica que `doc.size() <= maxFileSize` (configurable via `BussinesParams.MAX_FILE_SIZE`)
2. **Validación de patrón**: Verifica que el filename matchee el regex (configurable via `BussinesParams.REGEX`)

Ambas validaciones se leen desde `BussinesParamsGateway`, lo que permite cambiar los parámetros sin recompilar.

### Casos de Error en Validación

| Caso | Resultado |
|------|-----------|
| Archivo excede maxFileSize | Documento ignorado (Mono.empty()) |
| Filename no matchea regex | Documento ignorado (Mono.empty()) |

---

## Escenarios de Procesamiento de Documentos

### 1. Escenario: Procesamiento Exitoso

**Condición:** Todos los documentos se procesan sin errores.

```
Flujo:
1. executePendingDocuments() → obtiene productos desde REST API
2. Por cada documento pendiente:
   ├── validate() → pasa validación de tamaño y regex
   └── uploadDocument() → SOAP o S3 exitoso
3. Resultado = SUCCESS

Resultado:
- FileUploadResult.success = true
- correlationId = ID del servicio externo
```

### 2. Escenario: Fallo en Gateway

**Condición:** Error de red o servicio no disponible.

```
Flujo:
1. validate() → pasa validación
2. uploadDocument() → ProcessingException (timeout, 503, etc.)
3. onErrorResume() → status=FAILURE

Resultado:
- FileUploadResult.success = false
- errorCode = GATEWAY_TIMEOUT | BAD_GATEWAY | SERVICE_UNAVAILABLE
```

### 3. Escenario: Documento Ignorado por Validación

**Condición:** El documento no pasa las validaciones.

```
Flujo:
1. validate() → size excede límite O filename no matchea regex
2. log.warn() → "Document skipped"
3. return Mono.empty() → documento no se procesa

Resultado:
- Documento no aparece en resultados
- No se ejecuta uploadDocument()
```

### 4. Escenario: Claim Atómico (Concurrency)

**Condición:** Múltiples pods procesan el mismo documento.

```
Flujo:
1. Pod A y Pod B llaman getDocument() concurrently
2. Solo el primero en proceed continúa
3. El otro hace skip

Resultado:
- Solo 1 pod procesa el documento
- El otro continúa con el siguiente
```

---

## BussinesParamsGateway - Configuración Centralizada

`BussinesParamsGateway` es un puerto que permite obtener parámetros de negocio configurables:

### Parámetros Disponibles

| Enum | Descripción | Default |
|------|-------------|---------|
| `REGEX` | Patrón regex para validar filenames | `".*\\.(pdf\|csv)$"` |
| `MAX_FILE_SIZE` | Tamaño máximo de archivo en bytes | `52428800` (50MB) |

### Configuración en application.yml

```yaml
params:
  regex: ".*\\.(pdf|csv)$"
  maxFileSize: 52428800
```

### Implementación

`ProcessingProperties` implementa `BussinesParamsGateway` leyendo del prefijo `params.*` en `application.yml`.

---

## Códigos de Error (ProcessingResultCodes)

| Código | Descripción | Retry |
|--------|-------------|-------|
| `GATEWAY_TIMEOUT` | Timeout en gateway | Si |
| `BAD_GATEWAY` | Error 500 del servicio | Si |
| `SERVICE_UNAVAILABLE_ERROR` | Servicio no disponible | Si |
| `ACCESS_DENIED_ERROR` | Error 403 (S3) | No |
| `NOT_FOUND_ERROR` | Error 404 (S3) | No |
| `CLIENT_ERROR` | Error 4xx del servicio | No |
| `UNKNOWN_ERROR` | Error no categorizado | No |

---

## Estados de Documento (DocumentStatus)

```
SUCCESS    → Procesamiento exitoso
FAILURE    → Error permanente
```

---

## API Endpoints

### GET /api/v1/products/load

Carga productos desde REST API externa.

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
├── executePendingDocuments()     ← FINAL (template method)
│   ├── getAllProducts()          ← ProductRestGateway
│   ├── getDocument()              ← ProductRestGateway
│   ├── validate()                ← DocumentValidator
│   └── uploadDocument()          ← ABSTRACT (subclase implementa)
│
├── SoapDocumentProcessingUseCase
│   └── uploadDocument()          → SoapGateway.send()
│
└── S3DocumentProcessingUseCase
    └── uploadDocument()          → S3Gateway.send()
```

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
  soap:
    endpoint: ${SOAP_ENDPOINT}
    timeout-seconds: 30
    retry-attempts: 3
    retry-backoff-millis: 1000
  document-rest:
    endpoint: ${DOCUMENT_REST_ENDPOINT}
    products-path: /api/products
    product-documents-path: /api/products/{productId}/documents
    timeout-seconds: 15

params:
  regex: ".*\\.(pdf|csv)$"
  maxFileSize: 52428800

spring:
  r2dbc:
    url: r2dbc:h2:mem:///fileprocessor
```

---

## Seguridad

### Sanitización de S3 Keys

`S3GatewayAdapter.buildKey()` sanitiza el filename:
- Elimina `..` (path traversal)
- Elimina `/` y `\` (path separators)
- Previene escribir fuera del bucket

---

## Compilación y Ejecución

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Ejecutar con perfil SOAP
SPRING_PROFILES_ACTIVE=soap ./gradlew bootRun

# Ejecutar con perfil S3
SPRING_PROFILES_ACTIVE=s3 ./gradlew bootRun
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
