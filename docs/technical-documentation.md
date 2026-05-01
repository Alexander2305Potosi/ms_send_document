# Documentacion Tecnica - file-processor-service v3.0

## Indice
1. [Arquitectura General](#arquitectura-general)
2. [Endpoints REST](#endpoints-rest)
3. [Flujo de Datos](#flujo-de-datos)
4. [Base de Datos H2](#base-de-datos-h2)
5. [ Estados de Productos](#estados-de-productos)
6. [Gateway de Validacion](#gateway-de-validacion)
7. [Perfiles y Configuracion](#perfiles-y-configuracion)
8. [Ciclos de Fallos y Exitos](#ciclos-de-fallos-y-exitos)
9. [Commits Recientes](#commits-recientes)

---

## Arquitectura General

```
┌──────────────────────────────────────────────────────────────────────┐
│                      file-processor-service                           │
│                        Spring Boot 3.3.5                             │
│                      Java 21 + WebFlux                              │
├──────────────────────────────────────────────────────────────────────┤
│  DOMINIO                                                             │
│  ├── entity/          Product, ProductDocument, FileUploadResult    │
│  ├── usecase/         SoapDocumentProcessingUseCase                  │
│  │                    S3DocumentProcessingUseCase                   │
│  │                    SyncProductsUseCase                          │
│  ├── service/         RulesBussinesService                          │
│  ├── port/out/        ProductRestGateway                           │
│  │                    ProductDbGateway                            │
│  │                    RulesBussinesGateway                         │
│  │                    SoapGateway, S3Gateway                       │
│  └── exception/      ProcessingException                            │
├──────────────────────────────────────────────────────────────────────┤
│  APLICACION                                                         │
│  └── config/         DomainConfig                                   │
├──────────────────────────────────────────────────────────────────────┤
│  INFRAESTRUCTURA                                                    │
│  ├── drivenadapters/                                                │
│  │   ├── jpa/           ProductPersistenceAdapter                   │
│  │   │                    ProductDbAdapter                        │
│  │   │                    entity/PendingProductEntity              │
│  │   │                    repository/PendingProductRepository      │
│  │   ├── restclient/    ProductRestGatewayAdapter                  │
│  │   ├── soap/          SoapGatewayAdapter                         │
│  │   └── aws/           S3GatewayAdapter                          │
│  └── entrypoints/rest/                                              │
│       ├── handler/      ProductHandler                             │
│       ├── routes/       ProductRoutes                              │
│       └── constants/    RestApiPaths, ApiConstants                 │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Endpoints REST

### 1. GET `/api/v1/products`
Procesa documentos pendientes de productos en la fecha actual.

**Query Parameters:**
| Param | Valores | Default | Descripcion |
|-------|---------|---------|-------------|
| `processor` | `soap`, `s3` | `soap` | Tipo de procesamiento |

**Respuesta:** `Server-Sent Events (NDJSON)`
```json
{"correlationId":"corr-123","status":"SUCCESS","success":true,"processedAt":"2026-04-30T20:15:00Z"}
{"correlationId":"corr-124","status":"FAILURE","success":false,"errorCode":"INVALID_BASE64"}
```

**Comportamiento:**
- Consulta H2 por productos con `loadDate = hoy` y `state = PENDING`
- Por cada producto, obtiene documentos via `ProductRestGateway`
- Valida cada documento con `RulesBussinesService`
- Envía via SOAP o S3 segun parametro
- Retorna stream de `FileUploadResult`

---

### 2. POST `/api/v1/products/sync`
Sincroniza productos desde API REST externa hacia base de datos local H2.

**Respuesta:** HTTP 200 (siempre, async fire-and-forget)
```json
{"status":"OK","message":"Products sync initiated"}
```

**Comportamiento:**
- Consulta `ProductRestGateway.getAllProducts()`
- Por cada producto, crea registro en H2 con:
  - `productId`, `name`, `loadDate = LocalDateTime.now()`
  - `state = PENDING`
- El procesamiento real se hace via GET `/api/v1/products`

---

## Flujo de Datos

### Flujo de Sincronizacion (sync)
```
1. Cliente                          2. REST API Externa
   POST /api/v1/products/sync  ──►  GET /api/products
                                    ◄───────────────
                                         [Product, Product, ...]
                                             │
   ┌─────────────────────────────────────────┘
   ▼
3. ProductRestGatewayAdapter
   (convierte DTO → Domain)
   │
   ▼
4. SyncProductsUseCase.execute()
   │
   ▼
5. ProductPersistenceGateway.save()
   │
   ▼
6. PendingProductRepository.save()
   │
   ▼
7. H2 (pending_products table)
   │ productId | name | loadDate      | state   | messageError
   │-----------|------|---------------|---------|-------------
   │ prod-1   | Test | 2026-04-30... | PENDING | null
```

### Flujo de Procesamiento (execute)
```
1. Cliente
   GET /api/v1/products?processor=soap
   │
   ▼
2. ProductHandler.processPendingProducts()
   │
   ▼
3. AbstractDocumentProcessingUseCase.executePendingDocuments()
   │
   ▼
4. ProductDbGateway.findByLoadDate(LocalDate.now())
   │  → Filtra: loadDate = hoy AND state = PENDING
   ▼
5. H2 Query Result: [Product prod-1]
   │
   ▼
6. Por cada Product → Flux.fromIterable(documents)
   │
   ▼
7. ProductRestGateway.getDocument(productId, docId)
   │  → Obtiene documento completo (con contenido)
   ▼
8. RulesBussinesGateway.validate(document)
   │  → Valida tamano y patron filename
   ▼
9. uploadDocument() → SoapGateway.send() o S3Gateway.send()
   │
   ▼
10. FileUploadResult stream → Cliente (NDJSON)
```

---

## Base de Datos H2

### Tabla: `pending_products`

| Columna | Tipo | Constraints | Descripcion |
|--------|------|-------------|-------------|
| `product_id` | VARCHAR | PK | Identificador unico del producto |
| `name` | VARCHAR | NOT NULL | Nombre del producto |
| `load_date` | TIMESTAMP | NOT NULL | Fecha de carga (usada para filtrado diario) |
| `state` | VARCHAR | NOT NULL | Estado actual del producto |
| `message_error` | VARCHAR | NULLABLE | Mensaje de error si hubo falha |
| `created_at` | TIMESTAMP | NOT NULL | Fecha de creacion del registro |
| `updated_at` | TIMESTAMP | NOT NULL | Fecha de ultima actualizacion |

### Configuracion (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:fileprocessor;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    database-platform: org.hibernate.dialect.H2Dialect
  h2:
    console:
      enabled: true
      path: /h2-console
```

### Acceso a Consola H2
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:fileprocessor`
- User: `sa`
- Password: (vacío)

### Query de Filtrado
```java
// ProductDbAdapter.findByLoadDate()
findByLoadDateBetweenAndState(
    startOfDay,  // 2026-04-30 00:00:00.000
    endOfDay,    // 2026-04-30 23:59:59.999
    "PENDING"
)
```

---

## Estados de Productos

### Constantes (ProductState.java)
```java
public final class ProductState {
    public static final String PENDING = "PENDING";     // Nuevo, esperando procesamiento
    public static final String PROCESSED = "PROCESSED"; // Procesado exitosamente
    public static final String FAILED = "FAILED";        // Fallo en procesamiento
}
```

### Transiciones de Estado
```
[PENDING] ────► [PROCESSED]  Cuando todos los documentos se envian exitosamente
    │
    │
    ▼
[FAILED]      Cuando hay error irrecuperable en el procesamiento
```

### Uso en el Codigo
```java
// SyncProductsUseCase.java - al guardar producto nuevo
Product productToSave = new Product(
    product.productId(),
    product.name(),
    LocalDateTime.now(),
    ProductState.PENDING,  // ← estado inicial
    null,
    product.documents()
);

// ProductDbAdapter.java - al consultar para procesamiento
repository.findByLoadDateBetweenAndState(start, end, ProductState.PENDING)
```

---

## Gateway de Validacion

### Interfaz
```java
public interface RulesBussinesGateway {
    Mono<ProductDocument> validate(ProductDocument doc);
}
```

### Implementacion: RulesBussinesService
```java
public class RulesBussinesService implements RulesBussinesGateway {
    private final Long maxFileSizeBytes;
    private final Pattern filenamePattern;

    public RulesBussinesService(ProcessorsProperties.ProcessorConfig config) {
        this.maxFileSizeBytes = (config.maxFileSizeBytes() != null && config.maxFileSizeBytes() > 0)
            ? config.maxFileSizeBytes()
            : null;
        this.filenamePattern = (config.filenamePattern() != null && !config.filenamePattern().isBlank())
            ? Pattern.compile(config.filenamePattern())
            : null;
    }

    @Override
    public Mono<ProductDocument> validate(ProductDocument doc) {
        return Mono.defer(() -> {
            if (maxFileSizeBytes != null && doc.size() > maxFileSizeBytes) {
                log.debug("Document {} skipped: size {} exceeds max {}",
                    doc.documentId(), doc.size(), maxFileSizeBytes);
                return Mono.empty();
            }
            if (filenamePattern != null && !filenamePattern.matcher(doc.filename()).matches()) {
                log.debug("Document {} skipped: filename {} does not match pattern {}",
                    doc.documentId(), doc.filename(), filenamePattern.pattern());
                return Mono.empty();
            }
            return Mono.just(doc);
        });
    }
}
```

### Reglas de Validacion

| Regla | Config | Comportamiento |
|-------|--------|----------------|
| **Tamano maximo** | `processors.{soap,s3}.max-file-size-bytes` | Rechaza si `doc.size() > maxFileSizeBytes` |
| **Patron filename** | `processors.{soap,s3}.filename-pattern` | Rechaza si filename no matchea regex |

### Configuracion (application.yml)
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

## Perfiles y Configuracion

### Perfil: default (sin perfil)
- SOAP habilitado
- S3 deshabilitado (requiere perfil `s3`)
- H2 en memoria

### Perfil: `s3`
```yaml
spring:
  config:
    activate:
      on-profile: s3
app:
  aws:
    s3:
      bucket-name: documents-bucket
      region: us-east-1
      endpoint: http://localhost:4566
      path-style-access: true
      retry-attempts: 3
      retry-backoff-millis: 500
```

### Activar Perfil S3
```bash
./gradlew bootRun -Ps3
# o
java -jar app.jar --spring.profiles.active=s3
```

---

## Ciclos de Fallos y Exitos

### Ciclo Exitoso
```
Cliente ──► GET /api/v1/products?processor=soap
           │
           ▼
     [H2: consulta PENDING + hoy]
           │ productos = [P1, P2]
           ▼
     [ProductRestGateway: getDocument para cada doc]
           │ docs = [Doc1, Doc2, Doc3]
           ▼
     [RulesBussinesService: validate]
           │ todos pasan validacion
           ▼
     [SoapGatewayAdapter.send]
           │ response = SUCCESS
           ▼
     [FileUploadResult con success=true]
           │
           ▼
Cliente ◄── Stream NDJSON: {success:true, status:SUCCESS, ...}
```

### Ciclo con Validacion Fallida
```
Cliente ──► GET /api/v1/products?processor=soap
           │
           ▼
     [H2: consulta PENDING + hoy]
           │ productos = [P1]
           ▼
     [RulesBussinesService: validate]
           │ Doc1 size=500 > max=100 → FALLA
           ▼
     [Documento saltado, NO se envia a SOAP]
           │
           ▼
Cliente ◄── Stream NDJSON: (sin entrada para este doc)
           │ Solo se emiten resultados para docs que SÍ se procesaron
```

### Ciclo con Error en SOAP
```
Cliente ──► GET /api/v1/products?processor=soap
           │
           ▼
     [Documento validado OK]
           │
           ▼
     [SoapGatewayAdapter.send]
           │ exception = RuntimeException("SOAP error")
           ▼
     [handleUploadError()]
           │ errorCode = ProcessingResultCodes.UPLOAD_FAILED
           ▼
     [FileUploadResult con success=false, status=FAILURE]
           │
           ▼
Cliente ◄── {success:false, status:FAILURE, errorCode:UPLOAD_FAILED, ...}
```

### Codigos de Error (ProcessingResultCodes)
```java
public final class ProcessingResultCodes {
    public static final String EMPTY_CONTENT = "EMPTY_CONTENT";
    public static final String INVALID_BASE64 = "INVALID_BASE64";
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String UPLOAD_FAILED = "UPLOAD_FAILED";
}
```

---

## Commits Recientes

### v3.0 - Rama actual: `feature/v3.0`

| Commit | Descripcion |
|--------|-------------|
| `2f87e4f` | fix: filter products by state PENDING in findByLoadDate query |
| `1e99af4` | feat: read products from H2 database by loadDate |
| `586326e` | fix: wrap repository.save in Mono.fromRunnable for reactive transaction |
| `5e41b70` | refactor: extract PENDING state to ProductState constants |
| `7f71560` | fix: add domain package to ComponentScan for use case beans |
| `18f5df2` | fix: add @Component to SyncProductsUseCase for Spring autowiring |
| `07fabf2` | test: fix DocumentFlowIntegrationTest to use JDBC config for H2 |
| `c4e530a` | test: fix Product record constructor calls to match full signature |
| `47d31ed` | fix: correct async fire-and-forget pattern with subscribe() |
| `00a8c46` | fix: make sync endpoint async fire-and-forget |
| `845d98f` | feat: add product sync endpoint with H2 persistence |
| `c3f3baf` | refactor: inline validation logic in DefaultDocumentValidationService |

---

## Ejecucion

### Desarrollo
```bash
./gradlew bootRun
```

### Tests
```bash
./gradlew test
```

### Build
```bash
./gradlew build
```

### Endpoints disponibles
```bash
# Sincronizar productos
curl -X POST http://localhost:8080/api/v1/products/sync

# Procesar productos (SOAP)
curl http://localhost:8080/api/v1/products?processor=soap

# Procesar productos (S3)
curl http://localhost:8080/api/v1/products?processor=s3

# Consola H2
open http://localhost:8080/h2-console
```
