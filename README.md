# File Processor Service

Microservicio reactivo basado en Spring WebFlux + R2DBC que obtiene productos con sus documentos asociados desde una API REST externa, los persiste en base de datos H2 (desarrollo) o PostgreSQL (produccion) y los envia a un servicio SOAP externo o AWS S3.

---

## Tabla de Contenidos

1. [Arquitectura (Clean Architecture)](#arquitectura-clean-architecture)
2. [API Endpoints](#api-endpoints)
3. [Flujo de Datos](#flujo-de-datos)
4. [Base de Datos](#base-de-datos)
5. [Descompresion de archivos ZIP](#descompresion-de-archivos-zip)
6. [Estados de Documentos (ProductState)](#estados-de-documentos-productstate)
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
18. [Reglas de Negocio (Business Rules)](#reglas-de-negocio-business-rules)
19. [SOAP V2 — Generacion de Requests (SoapV2Mapper)](#soap-v2--generacion-de-requests-soapv2mapper)
20. [Stack Tecnologico](#stack-tecnologico)

---

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas estrictamente separadas. La capa de dominio es Java puro sin dependencias de frameworks. La capa de infraestructura contiene los adaptadores concretos (R2DBC, REST, SOAP, S3). La comunicacion entre capas se realiza a traves de puertos (interfaces en `port/out`).

```
com.example.fileprocessor/
├── Application.java                              # @SpringBootApplication (excluye WebMvc)
│
├── domain/                                       # Capa de dominio
│   ├── entity/
│   │   ├── Document.java                       # Record: metadatos del documento + estado actual (17 campos)
│   │   ├── DocumentHistory.java                # Record: trazabilidad de operaciones (13 campos, append-only)
│   │   ├── DocumentStatus.java                 # Enum: SUCCESS, FAILURE
│   │   ├── ProductDocumentFile.java            # Record: documento obtenido de REST API
│   │   ├── ProductDocumentHistory.java          # Record: documento (26 campos, incluye productId, isZip, pais, parentZipName)
│   │   ├── ProductState.java                   # Constantes de estado: PENDING, IN_PROGRESS, PROCESSED, FAILED
│   │   ├── FileUploadRequest.java              # Request para upload a gateway (SOAP/S3), incluye subTipoDocumental
│   │   ├── FileUploadResult.java               # Resultado de upload con status, errorCode, correlationId, traceId, attemptCount
│   │   ├── HomologationResult.java             # Resultado de homologacion (origin, paisHomologado, useV2)
│   │   ├── ExternalServiceResponse.java        # Respuesta generica de servicio externo
│   │   ├── ProductHistory.java                 # Record: producto con id, productId, name, loadDate, state, messageError
│   │   ├── CategoryManual.java                 # Record: homologacion de categoria (categoria, descripcionManual)
│   │   └── CountryHomologated.java             # Record: homologacion de pais (country, countryHomologated)
│   ├── usecase/
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base (procesa y descomprime ZIP en runtime)
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementacion SOAP (soporta V1 y V2, selecciona por HomologationResult.useV2)
│   │   ├── S3DocumentProcessingUseCase.java         # Implementacion S3
│   │   ├── SyncDocumentsUseCase.java                # Sincroniza productos y documentos desde API REST (sin validacion)
│   │   └── ProcessingResultCodes.java               # Constantes de codigos de error
│   ├── service/
│   │   └── RulesBussinesService.java              # Validacion: tamano maximo, patron filename
│   ├── util/
│   │   ├── ZipDecompressor.java                   # Descompresion de ZIP con inferencia de contentType
│   │   └── Base64Utils.java                       # Encoding/decoding seguro de Base64
│   ├── port/out/
│   │   ├── DocumentRepository.java               # Puerto: metadata de documentos (save, findByStateAndUseCase, updateStateById)
│   │   ├── DocumentHistoryRepository.java        # Puerto: trazabilidad (save, findLastAudit)
│   │   ├── ProductRepository.java               # Puerto: productos (findByLoadDate, findAll, save, updateEstadoById)
│   │   ├── ProductRestGateway.java                # Puerto: API REST externa de productos
│   │   ├── RulesBussinesGateway.java              # Puerto: validacion de documentos
│   │   ├── S3Gateway.java                         # Puerto: envio a S3
│   │   ├── SoapGateway.java                       # Puerto: envio a SOAP V1 (UploadFile)
│   │   ├── SoapGatewayV2.java                   # Puerto: envio a SOAP V2 (transmitirDocumento)
│   │   └── HomologationRepository.java           # Puerto: homologacion de origin y pais (SOAP)
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
    │   │   ├── DocumentR2dbcAdapter.java          # Implementa DocumentRepository (tabla documentos)
    │   │   ├── DocumentHistoryR2dbcAdapter.java    # Implementa DocumentHistoryRepository (tabla historico_documentos)
    │   │   ├── HomologationR2dbcAdapter.java      # Implementa HomologationRepository (cache en memoria)
    │   │   ├── entity/
    │   │   │   ├── DocumentEntity.java              # @Entity @Table("documentos") — metadata + estado
    │   │   │   ├── DocumentHistoryEntity.java      # @Entity @Table("historico_documentos") — trazabilidad
    │   │   │   ├── CategoryManualEntity.java       # @Entity @Table("categoria_manual")
    │   │   │   └── CountryHomologatedEntity.java  # @Entity @Table("pais_homologado")
    │   │   ├── mapper/
    │   │   │   ├── DocumentMapper.java              # Document <-> DocumentEntity
    │   │   │   └── DocumentHistoryMapper.java      # DocumentHistory <-> DocumentHistoryEntity
    │   │   └── repository/
    │   │       ├── DocumentRepository.java          # R2dbcRepository<DocumentEntity, Long> + @Query
    │   │       ├── DocumentHistoryRepository.java  # R2dbcRepository<DocumentHistoryEntity, Long> + @Query
    │   │       ├── CategoryManualRepository.java  # R2dbcRepository<CategoryManualEntity, Long>
    │   │       └── CountryHomologatedRepository.java # R2dbcRepository<CountryHomologatedEntity, Long>
    │   ├── restclient/
    │   │   ├── ProductRestGatewayAdapter.java     # WebClient a API REST externa (isZip inferido en dominio)
    │   │   └── dto/
    │   │       ├── ProductResponse.java            # DTO JSON de producto
    │   │       └── ProductDocumentResponse.java    # DTO JSON de documento (Base64)
    │   ├── soap/
    │   │   ├── SoapGatewayAdapter.java            # Envio SOAP con reintentos + backoff
    │   │   ├── SoapErrorCodes.java                # Constantes de error SOAP
    │   │   └── config/
    │   │       └── SoapProperties.java            # @ConfigurationProperties("app.soap")
    │   └── aws/
    │       ├── S3GatewayAdapter.java              # Envio S3 async con reintentos
    │       ├── S3ErrorCodes.java                  # Constantes de error S3
    │       └── config/
    │           ├── AwsConfig.java                 # Bean S3AsyncClient
    │           └── S3Properties.java              # @ConfigurationProperties("app.aws.s3")
    ├── entrypoints/rest/
    │   ├── ProductRoutes.java                    # Router function (WebFlux funcional)
    │   ├── handler/
    │   │   └── ProductHandler.java                # Handler de endpoints REST
    │   ├── config/
    │   │   └── DocumentRestProperties.java        # @ConfigurationProperties("app.document-rest")
    │   └── constants/
    │       ├── RestApiPaths.java                  # Rutas de la API
    │       └── ApiConstants.java                  # Constantes (headers, parametros)
    └── helpers/soap/
        ├── SoapConstants.java                   # Namespaces SOAP, templates XML
        ├── mapper/
        │   └── SoapMapper.java                   # JAXB V1 marshalling/unmarshalling + Base64
        ├── xml/
        │   ├── SoapEnvelopeWrapper.java         # Envoltorio SOAP con parseo DOM seguro
        │   └── model/
        │       ├── UploadFileRequest.java       # @XmlRootElement V1 request
        │       └── UploadFileResponse.java      # @XmlRootElement V1 response
        └── v2/
            ├── config/SoapV2Properties.java     # @ConfigurationProperties("app.soap.v2")
            ├── constants/SoapV2Constants.java   # W3C namespaces + prefijos estructurales
            ├── mapper/SoapV2Mapper.java         # buildEnvelope() StAX+JAXB + parseResponse()
            └── xml/
                ├── NamespaceInjectingStreamWriter.java  # Inyecta namespace en runtime
                └── model/
                    ├── body/                     # TransmitirDocumento, MetaDataWrapper, MetaDataEntry
                    └── header/                   # RequestHeader, UserId, Destination, etc.
```

### Recursos

```
src/main/resources/
├── application.yml              # Configuracion base
├── application-dev.yml         # Perfil desarrollo (DEBUG, timeouts cortos)
├── application-prod.yml         # Perfil produccion (WARN, graceful shutdown)
├── schema.sql                   # DDL para H2 (desarrollo)
└── schema-postgresql.sql         # DDL para PostgreSQL (produccion)

docs/migrations/
├── 001_create_documento_tables.sql  # Tabla historico_documentos (legacy, reemplazada en 005)
├── 002_alter_historico_documentos.sql
├── 003_audit_fields.sql
├── 004_add_sequences.sql             # Sequences para productos, categoria_manual, pais_homologado
└── 005_split_documentos_tables.sql  # Split en documentos + historico_documentos
```

---

## API Endpoints

### GET /api/v1/products

Procesa documentos pendientes desde la tabla `documentos` en estado PENDING. Cada documento se obtiene de la API REST externa, se descomprime si es ZIP, se valida (nombre + tamano), y se envia al gateway (SOAP o S3).

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion. Si no se envia, se genera un UUID automatico.

**Query Parameters:**
- `processor`: `soap` (default) | `s3` — Selecciona el gateway de salida.

**Response:** `Content-Type: application/x-ndjson` (NDJSON stream)
```json
{"correlationId":"corr-123","status":"SUCCESS","success":true,"processedAt":"2026-04-30T20:15:00Z","errorCode":null,"attemptCount":1}
{"correlationId":"corr-124","status":"FAILURE","success":false,"processedAt":"2026-04-30T20:15:01Z","errorCode":"GATEWAY_TIMEOUT","attemptCount":3}
```

**Errores:**
- `400 Bad Request` — Si `?processor=` tiene un valor no soportado.
- `503 Service Unavailable` — Si se solicita `?processor=s3` pero el perfil S3 no esta activo.

### POST /api/v1/products/sync

Sincroniza productos y documentos desde la API REST externa hacia la base de datos. Utiliza `productRepository.findAll()` para obtener todos los productos y luego `productRestGateway.getDocumentsByProduct()` para obtener los documentos de cada uno. Se persisten en la tabla `documentos` (metadata con `estado=PENDING`).

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion. Si no se envia, se genera un UUID automatico.
- `USE_CASE`: (opcional) Caso de uso para los documentos sincronizados (`soap` o `s3`). Default: `soap`.

**Response:** HTTP 200 (fire-and-forget — la operacion se ejecuta asincronamente)
```json
{"status":"OK","message":"Document sync initiated"}
```

### GET /actuator/health

Health check. Expone health, info, metrics, loggers y prometheus.

---

## Flujo de Datos

### Flujo de Sincronizacion (POST /api/v1/products/sync)

```
1. Cliente                           2. REST API Externa
   POST /api/v1/products/sync  ──►   GET {productDocumentsPath}/{productId}
   + Header: USE_CASE: soap/s3         Por cada producto
   ◄─────────────────────────────────
         [ProductDocumentHistory, ...]
               │
               ▼
3. SyncDocumentsUseCase.execute(useCase)
   ├── productRepository.findAll()
   │     └── SELECT * FROM productos
   ├── Por cada producto: productRestGateway.getDocumentsByProduct(product)
   │     └── GET {productDocumentsPath}/{productId} → Flux<ProductDocumentHistory>
   │     └── Decodifica Base64 via Base64Utils.decodeSafe()
   │     └── isZip se infiere de la extension del filename en el dominio
   ├── Por cada documento: documentRepository.save()
   │     └── INSERT en tabla documentos (estado=PENDING)
   │     └── No se guarda traza en historico_documentos durante sync
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
4. documentRepository.findByStateAndUseCase("PENDING", processor)
   └── Filtra: estado=PENDING AND caso_uso=SOAP/S3 en tabla documentos
        ▼
5. Por cada documento: startProcessing(doc)
   ├── documentRepository.updateStateById(docId, "PENDING", "IN_PROGRESS")
   │     └── Compare-and-set atomico: solo si estado actual=PENDING
   │     └── Si rowsAffected=0 → otro proceso ya lo claimo, se descarta
   │
   ▼
6. processDocument(doc)
   ├── productRestGateway.getDocument(doc.productId(), doc.documentId())
   │     └── GET {productDocumentsPath}/{documentId}
   │     └── Decodifica Base64 via Base64Utils.decodeSafe()
   ├── Si isZip=true → ZipDecompressor.decompress() expande entradas
   ├── RulesBussinesGateway.validate(doc, true)  [patron nombre + tamano]
   │     └── Si no pasa → documentRepository.updateStateById(PROCESSED), no se envia al gateway
   ├── uploadDocument()
   │     ├── SoapDocumentProcessingUseCase:
   │     │   ├── homologationRepository.resolve(origin, pais) → HomologationResult
   │     │   ├── Si useV2=true → SoapGatewayV2.transmitirDocumento() (SOAP V2)
   │     │   └── Si useV2=false → SoapGateway.send() (SOAP V1)
   │     └── S3DocumentProcessingUseCase:
   │           └── S3Gateway.send()
   ├── Exito → documentRepository.updateStateById(PROCESSED)
   │            historyRepository.save(trace: resultado=SUCCESS, operacion=SOAP/S3, retry)
   └── Error → documentRepository.updateStateById(PENDING o FAILED)
                historyRepository.save(trace: resultado=FAILURE, codigo_error, retry)
        │
        ▼
7. Flux<FileUploadResult> → NDJSON stream al cliente
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

### Tabla: documentos

Almacena los metadatos de cada documento y su estado actual de procesamiento. Una fila por documento unico (identificado por `id_documento`). El estado se actualiza conforme avanza el procesamiento.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador unico auto-generado |
| `id_documento` | VARCHAR(100) | ID del documento en el sistema externo |
| `id_producto` | VARCHAR(100) | ID del producto padre |
| `activo` | BOOLEAN | Si el documento esta activo (default: TRUE) |
| `clave_documento` | VARCHAR(255) | Clave de documento (nullable) |
| `nombre` | VARCHAR(255) | Nombre del archivo |
| `propietario` | VARCHAR(255) | Propietario del documento |
| `ruta` | TEXT | Ruta del documento (nullable) |
| `estado` | VARCHAR(100) | Estado: PENDING / IN_PROGRESS / PROCESSED / FAILED |
| `version_contrato` | VARCHAR(50) | Version de contrato (nullable) |
| `mensaje_error` | TEXT | Mensaje de error si hubo fallo |
| `es_zip` | BOOLEAN | Si es un archivo ZIP comprimido |
| `nombre_zip_padre` | VARCHAR(255) | Si viene de un ZIP, nombre del ZIP padre (nullable) |
| `caso_uso` | VARCHAR(100) | Caso de uso: SOAP o S3 |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |
| `fecha_actualizacion` | TIMESTAMP | Fecha de ultima actualizacion |

### Tabla: historico_documentos

Tabla **append-only** de trazabilidad/auditoria. Cada fila registra una operacion (SYNC, SOAP, S3) con su resultado. Nunca se actualiza — solo INSERT. La relacion con `documentos` es via FK numerica `documento_id`.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador unico auto-generado |
| `documento_id` | BIGINT (FK) | FK a `documentos.id` |
| `nombre_archivo` | VARCHAR(255) | Nombre del archivo descomprimido (solo para entradas de ZIP; null para archivos no comprimidos) |
| `operacion` | VARCHAR(50) | Tipo de operacion: SYNC, SOAP, S3 |
| `message_id` | VARCHAR(100) | Trace ID del request HTTP |
| `resultado` | VARCHAR(50) | SUCCESS / FAILURE |
| `codigo_error` | VARCHAR(50) | Codigo de error (GATEWAY_TIMEOUT, BAD_GATEWAY, BUSINESS_RULE_SKIP, INVALID_ZIP, etc.) |
| `mensaje_error` | TEXT | Mensaje de error |
| `stack_trace` | TEXT | Stack trace del error |
| `reintentos` | INTEGER | Numero de reintento (default: 0) |
| `fecha_inicio` | TIMESTAMP | Inicio de la operacion |
| `fecha_fin` | TIMESTAMP | Fin de la operacion |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

### Tabla: productos

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | SERIAL (PK) | Identificador unico auto-generado via secuencia `productos_id_seq` (INT4) |
| `id_producto` | VARCHAR(100) | ID del producto |
| `nombre` | VARCHAR(255) | Nombre del producto |
| `fecha_carga` | TIMESTAMP | Fecha de carga |
| `estado` | VARCHAR(100) | Estado del producto |
| `mensaje_error` | TEXT | Mensaje de error si hubo fallo |

### Tabla: categoria_manual

Almacena la homologacion de categorias de manuales. Se usa para resolver el `origin` de los documentos en el caso de uso SOAP usando busqueda contains.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | SERIAL (PK) | Identificador unico auto-generado via secuencia `categoria_manual_id_seq` (INT4) |
| `categoria` | VARCHAR(255) | Codigo de categoria (ej: "manual_tecnico") |
| `descripcion_manual` | VARCHAR(500) | Descripcion legible (ej: "Manual Tecnico del Producto") |
| `fecha_vigencia` | DATE | Fecha de vigencia |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

### Tabla: pais_homologado

Almacena la homologacion de paises. Se usa para resolver el `pais` de los documentos en el caso de uso SOAP.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | SERIAL (PK) | Identificador unico auto-generado via secuencia `pais_homologado_id_seq` (INT4) |
| `pais` | VARCHAR(255) | Codigo de pais (ej: "AR", "CL") |
| `pais_homologado` | VARCHAR(255) | Nombre homologado del pais (ej: "Argentina", "Chile") |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

### Indices

```sql
-- documentos
CREATE INDEX idx_documentos_estado ON documentos(estado);
CREATE INDEX idx_documentos_documento_id ON documentos(id_documento);
CREATE INDEX idx_documentos_producto_id ON documentos(id_producto);
CREATE INDEX idx_documentos_caso_uso ON documentos(caso_uso);

-- historico_documentos
CREATE INDEX idx_historico_documento_id ON historico_documentos(documento_id);
CREATE INDEX idx_historico_doc_operacion ON historico_documentos(documento_id, operacion, fecha_creacion DESC);

-- categoria_manual
CREATE INDEX idx_cat_manual_categoria ON categoria_manual(categoria);

-- pais_homologado
CREATE INDEX idx_pais_codigo ON pais_homologado(pais);
```
    
### DDL Completo

```sql
-- ============================================================================
-- Tabla: documentos
-- Almacena los metadatos de cada documento y su estado actual de procesamiento.
-- ============================================================================
CREATE TABLE IF NOT EXISTS documentos (
    id                  BIGSERIAL       PRIMARY KEY,
    id_documento        VARCHAR(100)    NOT NULL,
    id_producto         VARCHAR(100)    NOT NULL,
    activo              BOOLEAN         DEFAULT TRUE,
    clave_documento     VARCHAR(255),
    nombre              VARCHAR(255),
    propietario         VARCHAR(255),
    ruta                TEXT,
    estado              VARCHAR(100)    NOT NULL,
    version_contrato    VARCHAR(50),
    mensaje_error       TEXT,
    es_zip              BOOLEAN         DEFAULT FALSE,
    nombre_zip_padre    VARCHAR(255),
    caso_uso            VARCHAR(100),
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- Tabla: historico_documentos
-- Tabla append-only de trazabilidad/auditoria.
-- Cada fila registra una operacion (SYNC, SOAP, S3) con su resultado.
-- Nunca se actualiza — solo INSERT.
-- ============================================================================
CREATE TABLE IF NOT EXISTS historico_documentos (
    id                  BIGSERIAL       PRIMARY KEY,
    documento_id        BIGINT          NOT NULL REFERENCES documentos(id),
    nombre_archivo      VARCHAR(255),
    operacion           VARCHAR(50),
    message_id          VARCHAR(100),
    resultado           VARCHAR(50),
    codigo_error        VARCHAR(50),
    mensaje_error       TEXT,
    stack_trace         TEXT,
    reintentos          INTEGER         NOT NULL DEFAULT 0,
    fecha_inicio        TIMESTAMP,
    fecha_fin           TIMESTAMP,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documentos_estado ON documentos(estado);
CREATE INDEX IF NOT EXISTS idx_documentos_documento_id ON documentos(id_documento);
CREATE INDEX IF NOT EXISTS idx_documentos_producto_id ON documentos(id_producto);
CREATE INDEX IF NOT EXISTS idx_documentos_caso_uso ON documentos(caso_uso);
CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos(documento_id);
CREATE INDEX IF NOT EXISTS idx_historico_doc_operacion ON historico_documentos(documento_id, operacion, fecha_creacion DESC);

-- ============================================================================
-- Tabla: productos
-- ============================================================================
CREATE TABLE IF NOT EXISTS productos (
    id              SERIAL       PRIMARY KEY,
    id_producto     VARCHAR(100) NOT NULL,
    nombre          VARCHAR(255),
    fecha_carga    TIMESTAMP,
    estado          VARCHAR(100),
    mensaje_error   TEXT
);

-- ============================================================================
-- Tabla: categoria_manual
-- Homologacion de categorias de manuales para resolucion de origin en SOAP.
-- ============================================================================
CREATE TABLE IF NOT EXISTS categoria_manual (
    id                  SERIAL          PRIMARY KEY,
    categoria           VARCHAR(255)    NOT NULL UNIQUE,
    descripcion_manual  VARCHAR(500)    NOT NULL,
    fecha_vigencia      DATE,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cat_manual_categoria ON categoria_manual(categoria);

-- ============================================================================
-- Tabla: pais_homologado
-- Homologacion de paises para resolucion de pais en SOAP.
-- ============================================================================
CREATE TABLE IF NOT EXISTS pais_homologado (
    id              SERIAL       PRIMARY KEY,
    pais            VARCHAR(255)    NOT NULL UNIQUE,
    pais_homologado VARCHAR(255)    NOT NULL,
    fecha_creacion  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pais_codigo ON pais_homologado(pais);

-- ============================================================================
-- Sequences (para PostgreSQL)
-- ============================================================================
CREATE SEQUENCE IF NOT EXISTS historico_documentos_id_seq AS integer;
CREATE SEQUENCE IF NOT EXISTS productos_id_seq AS integer;
CREATE SEQUENCE IF NOT EXISTS categoria_manual_id_seq AS integer;
CREATE SEQUENCE IF NOT EXISTS pais_homologado_id_seq AS integer;
```


---

## Homologacion de Origin y Pais (SOAP)

El caso de uso SOAP realiza una homologacion de `origin` y `pais` antes de enviar el documento.

### Flujo de Homologacion

```
Documento.origin = "manual_tecnico"
        │
        ▼
Busca en categoria_manual (usa contains + eliminacion de tildes)
        │
        ▼
descripcion_manual = "Manual Tecnico del Producto"
        │
        ▼
Documento.pais = "AR"
        │
        ▼
Busca en pais_homologado WHERE pais = "AR"
        │
        ▼
pais_homologado = "Argentina"
        │
        ▼
FileUploadRequest.origin = "Manual Tecnico del Producto"
FileUploadRequest.paisHomologado = "Argentina"
```

### Busqueda con Contains y Eliminacion de Tildes

La homologacion de origin usa busqueda tipo `contains` con normalizacion de tildes:

1. Se normaliza el origin del documento eliminando tildes y conviertiendo a minusculas
2. Se itera sobre las categorias cargadas en cache
3. Se compara el origen normalizado contra cada clave de categoria (tambien normalizada)
4. Si la clave normalizada **contiene** el origin normalizado, se usa esa descripcion

Ejemplo:
- Documento.origin = "manual_tecnico"
- Categoria en cache: `categoria="manual_tecnico"` → `descripcion_manual="Manual Tecnico del Producto"`
- Resultado: origin se homologa a "Manual Tecnico del Producto"

### Cache en Memoria

`HomologationR2dbcAdapter` carga todas las categorias y paises una sola vez en `ConcurrentHashMap` al primer acceso. Las consultas siguientes usan el cache sin acceder a la base de datos. El cache se carga lazy (solo cuando se necesita por primera vez).

### Datos de Ejemplo

```sql
-- Categoria manual
INSERT INTO categoria_manual (categoria, descripcion_manual) VALUES
('manual_tecnico', 'Manual Tecnico del Producto'),
('manual_usuario', 'Manual de Usuario');

-- Pais homologado
INSERT INTO pais_homologado (pais, pais_homologado) VALUES
('AR', 'Argentina'),
('CL', 'Chile'),
('CO', 'Colombia');
```

### Acceso a Consola H2 (solo desarrollo)

- **URL:** `http://localhost:8080/h2-console`
- **JDBC URL:** `jdbc:h2:mem:fileprocessor`
- **User:** `sa`
- **Password:** (vacio)

### Consultas SQL utiles

```sql
-- Ver estado actual de todos los documentos
SELECT * FROM documentos ORDER BY fecha_creacion DESC;

-- Ver trazabilidad completa
SELECT * FROM historico_documentos ORDER BY fecha_creacion DESC;

-- Ver trazabilidad de un documento especifico (por documento_id FK)
SELECT * FROM historico_documentos WHERE documento_id = 12 ORDER BY fecha_creacion;

-- Ver solo envios SOAP
SELECT * FROM historico_documentos WHERE operacion = 'SOAP';

-- Ver envios con reintentos > 0
SELECT * FROM historico_documentos WHERE reintentos > 0 ORDER BY fecha_creacion DESC;

-- Ver documentos pendientes de procesamiento
SELECT * FROM documentos WHERE estado = 'PENDING';

-- Ver documentos fallidos
SELECT * FROM documentos WHERE estado = 'FAILED';

-- Ver documentos descomprimidos de un ZIP
SELECT * FROM documentos WHERE nombre_zip_padre = 'documents.zip';

-- Contar envios por operacion y resultado
SELECT operacion, resultado, COUNT(*) FROM historico_documentos GROUP BY operacion, resultado;

-- Ver ultimo evento de cada documento (con JOIN a documentos)
SELECT d.id_documento, d.nombre, h.nombre_archivo, h.operacion, h.resultado, h.codigo_error, h.reintentos, h.fecha_creacion
FROM historico_documentos h
JOIN documentos d ON d.id = h.documento_id
WHERE h.fecha_creacion = (
    SELECT MAX(fecha_creacion) FROM historico_documentos WHERE documento_id = h.documento_id
)
ORDER BY h.fecha_creacion DESC;
```

---

## Descompresion de archivos ZIP

`ZipDecompressor.decompress()` expande documentos ZIP. Se aplica **solo durante el procesamiento** (no durante sync), cuando un documento con `isZip=true` se descomprime para enviar cada entrada individualmente al gateway.

### Inferencia de isZip

`isZip` se infiere de la extension del archivo en la capa de dominio (`ProductDocumentHistory.isZip()`).

### Comportamiento durante Sync

| Escenario | Resultado |
|-----------|-----------|
| Documento normal (`isZip=false`) | Se guarda en `documentos` (estado=PENDING) y se registra en `historico_documentos` con `nombre_archivo=NULL` |
| Documento ZIP (`isZip=true`) | Se guarda en `documentos` (estado=PENDING, `nombre_zip_padre=NULL`, `isZip=true`). Traza SYNC con `nombre_archivo=NULL`. **No se expande durante sync** |

### Comportamiento durante Procesamiento

| Escenario | Resultado |
|-----------|-----------|
| Documento normal (`isZip=false`) | Se procesa directamente. Traza con `nombre_archivo=NULL` (el nombre ya esta en `documentos.nombre`) |
| Documento ZIP (`isZip=true`) | `ZipDecompressor.decompress()` expande cada entrada. Cada una se procesa con traza `nombre_archivo=entry_filename` y `documento_id=PK_del_ZIP` |

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

Un documento ZIP con `id_documento=PROD-001/documents.zip` y `nombre=documents.zip` en `documentos` (id=12, isZip=true). Durante el procesamiento, `ZipDecompressor.decompress()` expande su contenido:

```
documentos:
  id=12  → id_documento="PROD-001/documents.zip", isZip=true, nombre_zip_padre=NULL

Procesamiento (runtime):
  documents.zip (id=12) se descomprime:
    ├── data.csv  → traza: documento_id=12, nombre_archivo="data.csv"
    └── guide.pdf → traza: documento_id=12, nombre_archivo="guide.pdf"
```

> Las entradas expandidas **no** tienen su propia fila en `documentos`. Solo existen durante el procesamiento. Todas las trazas en `historico_documentos` referencian `documento_id=12` (PK del ZIP padre). `nombre_archivo` es lo que permite distinguir que entrada se proceso en cada traza.

---

## Estados de Documentos (ProductState)

```java
public final class ProductState {
    public static final String PENDING     = "PENDING";     // Esperando procesamiento
    public static final String IN_PROGRESS = "IN_PROGRESS"; // En procesamiento actual
    public static final String PROCESSED   = "PROCESSED";   // Enviado exitosamente
    public static final String FAILED      = "FAILED";      // Agoto reintentos o fallo permanente
}
```

### Transiciones de State

```
               sync (POST /api/v1/products/sync)
                 │
                 ▼
             [PENDING]          ← guardado en tabla documentos
                 │
                 │  executePendingDocuments()
                 ▼
          [IN_PROGRESS]
                 │
        ┌────────┴────────┐
        ▼                 ▼
    [PROCESSED]        [FAILED]
    (envio OK)     (reintentos agotados)
    (o skip)
```

---

## Validacion de Documentos (RulesBussinesService)

`RulesBussinesService` implementa validacion **solo durante el procesamiento**:

### Procesamiento (Processing)

Se aplica durante `GET /api/v1/products`. Valida tanto el **tamano maximo** como el **patron de nombre de archivo**. Si un documento no pasa la validacion, se marca como `PROCESSED` (skip) sin enviarse al gateway.

| Regla | Processing |
|-------|------------|
| **Tamano maximo** | Omite si `doc.size() > max` |
| **Patron filename** | Omite si no coincide |

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

### Reintentos en Gateway

Ambos gateways (SOAP y S3) implementan reintentos automaticos con backoff.

**SOAP Gateway:**
- Reintentos: `app.soap.retry-attempts` (default: 3)
- Backoff: 500ms fijo
- Condiciones reintentables: HTTP 503, 502, 504, 429, `TimeoutException`, `ConnectException`

**S3 Gateway:**
- Reintentos: `app.aws.s3.retry-attempts` (default: 3)
- Backoff: `app.aws.s3.retry-backoff-millis` (default: 500ms)
- Condiciones reintentables: `TimeoutException`, `SdkException`

---

## Escenarios de Procesamiento

### 1. Exitoso
```
Documento PENDING → IN_PROGRESS
uploadDocument() → SUCCESS
documentRepository.updateStateById() → estado=PROCESSED en documentos
historyRepository.save() → INSERT en historico_documentos (resultado=SUCCESS, operacion=SOAP/S3, nombre_archivo=NULL si no es entrada ZIP, retry=0)
Stream: {"success":true, "status":"SUCCESS"}
```

### 2. Error en Gateway (con reintentos)
```
uploadDocument() → exception
historyRepository.findLastAudit(docId, useCase) → obtiene retry count (filtrado por operacion)
documentRepository.updateStateById() → estado=PENDING o FAILED en documentos
historyRepository.save() → INSERT en historico_documentos (resultado=FAILURE, codigo_error, reintentos=N, operacion=SOAP/S3, nombre_archivo=NULL si no es entrada ZIP)
Stream: {"success":false, "status":"FAILURE", "errorCode":"GATEWAY_TIMEOUT", "retry":3}
```

### 3. Documento ZIP
```
Processing: isZip=true → ZipDecompressor.decompress() expande cada entrada en runtime
           → Cada entrada se procesa independientemente
           → Todas las trazas comparten documento_id=PK_del_ZIP
           → Cada traza tiene nombre_archivo=entry_filename (diferencia cada entrada)
```

### 4. Error en Descompresion ZIP
```
ZipDecompressor.decompress() → ProcessingException(INVALID_ZIP)
historyRepository.save() → INSERT en historico_documentos (errorCode=INVALID_ZIP, nombre_archivo=NULL, documento_id=PK_del_ZIP)
documentRepository.updateStateById() → estado=FAILED
```

### 5. Skip por Regla de Negocio
```
RulesBussinesService.validate() → no pasa (extension o tamano)
documentRepository.updateStateById() → estado=PROCESSED en documentos
historyRepository.save() → INSERT en historico_documentos (resultado=FAILURE, codigo_error=BUSINESS_RULE_SKIP, nombre_archivo=NULL si no es entrada ZIP)
```

### 6. Error de Base64
```
Base64Utils.decodeSafe() → InvalidBase64Exception(INVALID_BASE64)
historyRepository.save() → INSERT en historico_documentos (errorCode=INVALID_BASE64)
documento no se guarda / no se procesa
```

---

## Codigos de Error

### Errores de Dominio (ProcessingResultCodes)

Definidos en `domain/usecase/ProcessingResultCodes.java`:

| Codigo | Descripcion |
|--------|------------|
| `EMPTY_CONTENT` | Documento sin contenido |
| `INVALID_BASE64` | Fallo al decodificar Base64 |
| `INVALID_RESPONSE` | Respuesta SOAP invalida o malformada |
| `INVALID_ZIP` | Archivo ZIP corrupto o invalido |
| `BUSINESS_RULE_SKIP` | Documento omitido por regla de negocio (extension o tamano) |
| `REST_CLIENT_ERROR` | Fallo al obtener documento desde REST API |
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

La trazabilidad se divide en dos tablas con relacion FK:

- **`documentos`**: Una fila por documento. Contiene metadata y estado actual. Se actualiza conforme avanza el procesamiento.
- **`historico_documentos`**: Append-only. Cada operacion (SYNC, SOAP, S3) inserta una nueva fila con `documento_id` (FK), `nombre_archivo`, `operacion`, `resultado`, `codigo_error`, `reintentos`, y timestamps.

### Campos clave

- **`documento_id`** (FK a `documentos.id`): Relacion numerica con la tabla de metadata.
- **`nombre_archivo`**: Nombre del archivo descomprimido. Solo se popula para entradas extraidas de un ZIP (`parentZipName != null`). Para archivos normales es `NULL` (el nombre ya esta en `documentos.nombre`).
- **`operacion`**: Tipo de operacion — SYNC (sync inicial), SOAP (envio SOAP), S3 (envio S3).
- **`resultado`**: SUCCESS o FAILURE.
- **`reintentos`**: Numero de intento (0 = primer intento, 1 = primer reintento, etc.).

### Flujo de Persistencia (dos tablas)

```
uploadDocument() → FileUploadResult
        │
        ▼
handleUploadSuccess() o handleUploadError()
        │
        ├── documentRepository.updateStateById(doc.id(), newState)
        │     └── UPDATE documentos SET estado = ?, fecha_actualizacion = ?
        │
        └── historyRepository.save(trace)
              └── INSERT INTO historico_documentos (documento_id, nombre_archivo, operacion, resultado, codigo_error, reintentos, ...)
```

### Ejemplo de Trazabilidad Completa

Supongamos un producto `PROD-001` con los siguientes documentos obtenidos desde la API REST:

- `manual.pdf` (PDF independiente, 1 MB, **no** proviene de un ZIP)
- `documents.zip` (ZIP, contiene `data.csv` + `guide.pdf`)

Y el procesamiento via SOAP (`processor=soap`).

#### Paso 1 — Sync (POST /api/v1/products/sync)

Se guardan en `documentos` (**solo los documentos originales**, sin expandir el ZIP):

| id | id_documento | nombre | estado | es_zip | nombre_zip_padre | caso_uso |
|----|-------------|--------|--------|--------|------------------|----------|
| 11 | PROD-001/manual.pdf | manual.pdf | PENDING | false | NULL | SOAP |
| 12 | PROD-001/documents.zip | documents.zip | PENDING | true | NULL | SOAP |

Y en `historico_documentos` las trazas SYNC:

| id | documento_id | nombre_archivo | operacion | resultado | reintentos | fecha_inicio | fecha_fin |
|----|-------------|----------------|-----------|-----------|------------|--------------|-----------|
| 101 | 11 | NULL | SYNC | SUCCESS | 0 | 10:00:00 | 10:00:00 |
| 102 | 12 | NULL | SYNC | SUCCESS | 0 | 10:00:00 | 10:00:01 |

> `manual.pdf` (id=11) **no es un archivo comprimido**: `nombre_archivo=NULL`. El nombre ya esta en `documentos.nombre`.
> `documents.zip` (id=12) es un ZIP contenedor: `nombre_archivo=NULL`. Durante el sync no hay descompresion — los documentos se guardan tal cual llegan de la API REST.

#### Paso 2 — Procesamiento (GET /api/v1/products?processor=soap)

**Intento 1:** `manual.pdf` (id=11) se procesa exitosamente. `documents.zip` (id=12) se descomprime en runtime → `data.csv` se envia con exito, `guide.pdf` falla con timeout.

| id | documento_id | nombre_archivo | operacion | resultado | codigo_error | reintentos | fecha_inicio | fecha_fin |
|----|-------------|----------------|-----------|-----------|-------------|------------|--------------|-----------|
| 103 | 11 | NULL | SOAP | SUCCESS | NULL | 0 | 10:05:00 | 10:05:02 |
| 104 | 12 | data.csv | SOAP | SUCCESS | NULL | 0 | 10:05:02 | 10:05:03 |
| 105 | 12 | guide.pdf | SOAP | FAILURE | GATEWAY_TIMEOUT | 1 | 10:05:03 | 10:05:33 |

Estados en `documentos` tras Intento 1:

| id | estado |
|----|--------|
| 11 | PROCESSED |
| 12 | PENDING |

> `manual.pdf` (id=11): `nombre_archivo=NULL`. Es un archivo independiente, no proviene de un ZIP. El nombre ya esta en `documentos.nombre`.
> `data.csv` (documento_id=12): `nombre_archivo=data.csv` porque `parentZipName=documents.zip`. Identifica que entrada del ZIP se proceso.
> `guide.pdf` (documento_id=12): `nombre_archivo=guide.pdf` porque `parentZipName=documents.zip`. Fallo con `retry=1`. Como `retry < 3`, el estado del documento id=12 vuelve a `PENDING`.

**Intento 2** (siguiente ejecucion del pipeline): `documents.zip` (id=12, estado PENDING) se retoma. La descompresion en runtime genera `guide.pdf`, que esta vez se procesa exitosamente.

| id | documento_id | nombre_archivo | operacion | resultado | reintentos | fecha_inicio | fecha_fin |
|----|-------------|----------------|-----------|-----------|------------|--------------|-----------|
| 106 | 12 | guide.pdf | SOAP | SUCCESS | 0 | 10:10:00 | 10:10:01 |

Estado final en `documentos`:

| id | id_documento | nombre | estado | es_zip | nombre_zip_padre |
|----|-------------|--------|--------|--------|------------------|
| 11 | PROD-001/manual.pdf | manual.pdf | PROCESSED | false | NULL |
| 12 | PROD-001/documents.zip | documents.zip | PROCESSED | true | NULL |

#### Resumen: ¿cuándo se guarda `nombre_archivo`?

| Caso | documento_id | `nombre_archivo` | Motivo |
|------|-------------|------------------|--------|
| Archivo independiente (`manual.pdf`) | 11 | `NULL` | No es entrada de ZIP. El nombre esta en `documentos.nombre` |
| Entrada de ZIP (`data.csv`) | 12 | `"data.csv"` | Proviene de `ZipDecompressor`. Identifica que archivo del ZIP se proceso |
| Entrada de ZIP (`guide.pdf`) | 12 | `"guide.pdf"` | Mismo `documento_id=12` que `data.csv`. `nombre_archivo` diferencia cada entrada |

---

## Template Method Pattern

El patron se implementa en `AbstractDocumentProcessingUseCase`:

```
AbstractDocumentProcessingUseCase
│
├── executePendingDocuments()           ← FINAL (template method)
│   ├── documentRepository.findByStateAndUseCase("PENDING", processor)  → BD (tabla documentos)
│   ├── Por cada documento: startProcessing(doc)
│   │   ├── documentRepository.updateStateById(docId, "PENDING", "IN_PROGRESS")
│   │   │     └── Compare-and-set atomico: solo si estado=PENDING
│   │   │     └── Si rowsAffected=0 → otro proceso ya lo claimo, se descarta
│   │   └── processDocument(doc)
│   │       ├── productRestGateway.getDocument(productId, docId) → REST externa
│   │       ├── toProductDocument(file) → ProductDocumentHistory
│   │       ├── Si isZip=true → ZipDecompressor.decompress() expande entradas
│   │       ├── documentValidator.validate(doc, true) → validacion nombre + tamano
│   │       ├── uploadDocument()       → ABSTRACT (SOAP o S3)
│   │       └── handleSuccess() o handleError()
│   │             └── documentRepository.updateStateById() + historyRepository.save()
│   │
│
├── uploadDocument()                     ← ABSTRACT
│   └── buildFileUploadRequest()         ← helper protegido
│
├── handleUploadError(Throwable)          ← helper protegido (reusable por subclases)
│
├── SoapDocumentProcessingUseCase
│   └── uploadDocument()
│       ├── homologationRepository.resolve(origin, pais) → HomologationResult
│       ├── Si useV2=true → SoapGatewayV2.transmitirDocumento() (SOAP V2)
│       └── Si useV2=false → SoapGateway.send() (SOAP V1)
│
└── S3DocumentProcessingUseCase
    └── uploadDocument() → S3Gateway.send()
```

### Subclases

**SoapDocumentProcessingUseCase** — Envia documentos via SOAP con homologacion de origin y pais. Bean definido en `DomainConfig`.

**S3DocumentProcessingUseCase** — Envia documentos via S3. Bean condicional (`@ConditionalOnBean(S3Gateway.class)`) definido en `DomainConfig`. Se usa `ObjectProvider` en el handler para manejar su disponibilidad opcional.

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
# Sincronizar productos y documentos desde API REST a BD
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

## Reglas de Negocio (Business Rules)

Esta seccion documenta todas las reglas de negocio del microservicio para servir como referencia en futuras validaciones. Cualquier modificacion que altere estos comportamientos debe considerarse un cambio funcional que requiere revision.

---

### 1. Ciclo de Vida de Documentos (ProductState)

El estado de cada documento se rige por la maquina de estados definida en `ProductState.java`:

| Estado | Significado | Disparador |
|--------|-------------|------------|
| `PENDING` | Esperando ser procesado | Estado inicial de documentos listos para procesar |
| `IN_PROGRESS` | En procesamiento actual | Se asigna justo antes de iniciar `processDocument()` |
| `PROCESSED` | Enviado exitosamente o skip por validacion | Upload exitoso O documento no pasa validacion de tamaño/patron |
| `FAILED` | Agoto reintentos o fallo permanente | Error de envio con `retry >= MAX_RETRIES` (3) |

**Transiciones validas:**

```
PENDING --> IN_PROGRESS --> PROCESSED
                            |
                            +--> PENDING (si retry < 3)
                            +--> FAILED  (si retry >= 3)
```

**Reglas:**

- **RB-01:** Un documento en estado `PROCESSED` no puede ser reprocesado. `canResume()` consulta el ultimo audit y retorna `false` si el estado es `PROCESSED`.
- **RB-02:** La transicion a `IN_PROGRESS` se ejecuta en fire-and-forget (`.subscribe()` sin esperar) antes de iniciar el procesamiento.
- **RB-03:** Si un documento no pasa la validacion (tamaño o patron de nombre), se marca como `PROCESSED` sin enviarse al gateway. Esto es un skip intencional, no un error.

---

### 2. Reintentos (Retry Logic)

La logica de reintentos opera en dos niveles independientes:

#### 2.1 Reintentos a Nivel de Dominio

Definidos en `AbstractDocumentProcessingUseCase`:

- **RB-04:** `MAX_RETRIES = 3`. Un documento puede fallar hasta 3 veces antes de marcarse como `FAILED`.
- **RB-05:** El contador de reintentos (`retry`) incrementa en +1 por cada intento fallido.
- **RB-06:** Si `retry < 3` → el documento vuelve a `PENDING` y sera reintentado en la siguiente ejecucion de `executePendingDocuments()`.
- **RB-07:** Si `retry >= 3` → el documento pasa a `FAILED` y no se vuelve a intentar automaticamente.
- **RB-08:** `handleUploadError()` consulta el ultimo intento via `findLastAudit(docId, useCase)` (filtrado por `operacion`), calcula `retry + 1`, actualiza el estado en `documentos` (PENDING si retry<3, FAILED si >=3), guarda la traza en `historico_documentos`, y emite un `FileUploadResult` con `success=false`.

#### 2.2 Reintentos a Nivel de Gateway

**SOAP Gateway** (`SoapGatewayAdapter`):
- **RB-09:** Reintentos configurados via `app.soap.retry-attempts` (default: 3).
- **RB-10:** Backoff fijo de 500ms entre reintentos.
- **RB-11:** Condiciones reintentables: HTTP 503, 502, 504, 429, `TimeoutException`, `ConnectException`.
- **RB-12:** Errores no reintentables (ej: HTTP 400, 500) fallan inmediatamente.

**S3 Gateway** (`S3GatewayAdapter`):
- **RB-13:** Reintentos configurados via `app.aws.s3.retry-attempts` (default: 3) con backoff exponencial (`retry-backoff-millis`, default: 500ms).
- **RB-14:** Condiciones reintentables: `TimeoutException`, `SdkException` con nombres que contengan `ServiceException`, `SocketTimeoutException`, o `ConnectTimeoutException`.
- **RB-15:** Contenido null o vacio (length=0) no se reintenta → falla inmediatamente con `EMPTY_CONTENT`.

---

### 3. Validacion de Documentos

`RulesBussinesService` aplica dos tipos de validacion. Su comportamiento difiere segun el flujo.

#### 3.1 Durante Sync (`POST /api/v1/products/sync`)

- **RB-16:** No se aplica validacion de tamaño ni de patron de nombre. Todos los documentos se guardan tal cual llegan de la API REST externa.

#### 3.2 Durante Procesamiento (`GET /api/v1/products`)

- **RB-17:** Se valida **tamaño maximo** (`maxFileSizeBytes`): si `doc.size() > maxFileSizeBytes`, el documento se omite y se marca `PROCESSED` sin enviar al gateway.
- **RB-18:** Se valida **patron de nombre** (`filenamePattern`): si el nombre no coincide con la expresion regular, el documento se omite y se marca `PROCESSED` sin enviar al gateway.
- **RB-19:** El orden de validacion es: primero tamaño, luego patron. Si falla cualquiera, se retorna `Mono.empty()` (señal de skip).
- **RB-20:** Si `maxFileSizeBytes` es null o ≤0, no se aplica validacion de tamaño. Si `filenamePattern` es null o blank, no se aplica validacion de nombre.

**Configuracion por defecto:**

| Procesador | Tamaño maximo | Patron de nombre |
|-----------|---------------|------------------|
| SOAP | 10 MB (`10485760`) | `.*\.(pdf|docx|txt)$` |
| S3 | 50 MB (`52428800`) | `.*\.(pdf|csv)$` |

---

### 4. Descompresion de Archivos ZIP

Gestionada por `ZipDecompressor`:

- **RB-21:** `isZip` se infiere de la extension del archivo en el dominio (`ProductDocumentHistory.isZip()`).
- **RB-22:** Solo se descomprime si `isZip=true` y el filename no es null/blank. La descompresion ocurre en **runtime durante el procesamiento** — las entradas expandidas no se persisten en `documentos`.
- **RB-23:** Cada entrada del ZIP se expande como un objeto `ProductDocumentHistory` en memoria con:
  - `documentId = documentIdOriginal + "/" + nombreEntrada` (ej: `PROD-001/documents.zip/data.csv`)
  - `parentZipName = filename del ZIP original` (ej: `documents.zip`)
  - `isZip = false`
  - `contentType` inferido por extension (`.pdf`→`application/pdf`, `.csv`→`text/csv`, `.txt`→`text/plain`, `.docx`→`application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `.xlsx`→`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, otro→`application/octet-stream`)
- **RB-24:** Los directorios dentro del ZIP se ignoran (solo se procesan archivos).
- **RB-25:** Si el ZIP esta corrupto o es invalido → `ProcessingException` con codigo `INVALID_ZIP`.
- **RB-26:** Si el ZIP esta vacio (sin entradas validas) → retorna `Flux.empty()`.

---

### 5. Decodificacion Base64

Gestionada por `Base64Utils`:

- **RB-27:** Si el contenido Base64 es null o blank → `InvalidBase64Exception` con codigo `EMPTY_CONTENT`.
- **RB-28:** Si el contenido Base64 no es valido (caracteres invalidos, padding incorrecto) → `InvalidBase64Exception` con codigo `INVALID_BASE64`.
- **RB-29:** En `ProductRestGatewayAdapter.mapToProductDocument()`, el tamaño se calcula como `json.size()` si esta presente, o se infiere de `content.length` tras decodificar. Si ambos son null → tamaño = 0.

---

### 6. Homologacion de Origin y Pais (SOAP)

Gestionada por `HomologationR2dbcAdapter`:

- **RB-30:** La homologacion solo se aplica en el caso de uso **SOAP** (`SoapDocumentProcessingUseCase`). El caso de uso S3 no homologa.
- **RB-31:** La cache de categorias y paises se carga **una sola vez** (lazy, en el primer acceso) desde base de datos y se almacena en `ConcurrentHashMap`.
- **RB-32:** `resolveOrigin()` usa busqueda **contains** con normalizacion de tildes:
  - Se eliminan tildes y se convierte a minusculas tanto el input como las claves de cache
  - Si la clave normalizada **contiene** el origin normalizado → se usa la descripcion asociada
  - Sin match → se retorna el valor original sin modificar
  - Si origin es null/blank → se retorna sin cambios
  - Si la descripcion asociada es null → se retorna el valor original
- **RB-33:** `resolveCountry()` usa busqueda **exacta** en el mapa de paises:
  - Si el codigo de pais existe en cache → retorna el nombre homologado
  - Sin match → retorna el codigo original
  - Si country es null/blank → retorna sin cambios
  - Si el nombre homologado es null → retorna el codigo original

---

### 7. Seleccion de Procesador (Processor)

Gestionada por `ProductHandler.getProcessor()`:

- **RB-34:** El query param `processor` es opcional. Si no se especifica, se usa `soap` por defecto.
- **RB-35:** `processor=soap` → siempre disponible. Usa `SoapDocumentProcessingUseCase`.
- **RB-36:** `processor=s3` → requiere que el perfil `s3` este activo. Si no lo esta → `503 Service Unavailable`.
- **RB-37:** Cualquier otro valor de `processor` → `400 Bad Request` con mensaje descriptivo.

---

### 8. Trace ID y Trazabilidad

- **RB-38:** El header HTTP `message-id` es opcional. Si el cliente lo envia, se usa como `traceId`.
- **RB-39:** Si `message-id` no se envia o esta blank → se genera un UUID v4 aleatorio.
- **RB-40:** El `traceId` se propaga a traves de todo el flujo reactivo via `Context` de Reactor (`ctx.put(HEADER_TRACE_ID, traceId)`).
- **RB-41:** Cada intento de envio a gateway queda registrado en `historico_documentos` con `operacion` (SOAP o S3) y `documento_id` (FK a `documentos.id`), permitiendo auditoria completa por tipo de operacion.

---

### 9. Flujo Sync (Fire-and-Forget)

- **RB-42:** `POST /api/v1/products/sync` retorna HTTP 200 inmediatamente con `{"status":"OK","message":"Document sync initiated"}`.
- **RB-43:** La ejecucion real de `SyncDocumentsUseCase.execute()` se dispara con `.subscribe()` sin esperar su resultado (fire-and-forget).
- **RB-44:** Los documentos sincronizados se guardan en la tabla `documentos` (metadata con `estado=PENDING`). No se escribe en `historico_documentos` durante sync — la traza se registra unicamente durante el procesamiento efectivo (SOAP o S3).
- **RB-45:** Si un documento es ZIP, se guarda con `isZip=true` y `nombre_zip_padre=NULL` en `documentos`. `parentZipName` solo se asigna a entradas extraidas de un ZIP durante el procesamiento, nunca durante sync.

---

### 10. Trazabilidad en Dos Tablas

- **RB-46:** La metadata y la trazabilidad se almacenan en tablas separadas con FK numerica:
  - **`documentos`**: metadata + estado actual (una fila por documento, se actualiza)
  - **`historico_documentos`**: trazabilidad append-only (cada operacion inserta una nueva fila con `documento_id` FK)
- **RB-47:** Cada operacion escribe en ambas tablas: `documentRepository.updateStateById()` actualiza el estado en `documentos`, y `historyRepository.save()` inserta una traza en `historico_documentos`.
- **RB-48:** `findLastAudit(documentoId, useCase)` consulta el registro de trazabilidad mas reciente para un `documento_id` y `operacion` especifica (ej: SOAP). Filtra por `documento_id` + `operacion` para no mezclar trazas SYNC con SOAP/S3. Retorna empty si no hay registros previos.
- **RB-49:** `nombre_archivo` en `historico_documentos` solo se popula para entradas extraidas de un ZIP (`parentZipName != null`). Para archivos no comprimidos es `NULL`, ya que el nombre del archivo ya esta disponible en `documentos.nombre`. Esto evita redundancia y permite identificar inequivocamente que archivos provienen de descompresion ZIP.

---

## SOAP V2 — Generacion de Requests (SoapV2Mapper)

`SoapV2Mapper` es el componente encargado de construir el envelope SOAP 1.1 para el protocolo V2 (`transmitirDocumento`) y de parsear las respuestas. La generacion del XML combina **StAX** (`XMLStreamWriter`) para la estructura del envelope y **JAXB** para marshalling de header y body con anotaciones `@XmlElement`.

### Paquete `v2/`

```
infrastructure/helpers/soap/v2/
├── config/
│   └── SoapV2Properties.java              ← @ConfigurationProperties("app.soap.v2")
├── constants/
│   └── SoapV2Constants.java               ← Namespaces W3C + prefijos + elementos estructurales
├── mapper/
│   └── SoapV2Mapper.java                  ← Logica principal: buildEnvelope() + parseResponse()
├── xml/
│   ├── NamespaceInjectingStreamWriter.java ← Decorador XMLStreamWriter (inyecta namespace dinamico)
│   └── model/
│       ├── body/
│       │   ├── TransmitirDocumentoRequest.java   ← @XmlRootElement del body
│       │   ├── TransmitirDocumentoResponse.java  ← @XmlRootElement del response
│       │   ├── MetaDataWrapper.java              ← Wrapper de <metaData>
│       │   └── MetaDataEntry.java                ← Entry individual <nombre>/<valor>
│       └── header/
│           ├── SoapV2RequestHeader.java          ← @XmlRootElement del header (requestHeader)
│           ├── SoapV2UserId.java                 ← <userName> + <userToken>
│           ├── SoapV2Destination.java            ← <name> + <namespace> + <operation>
│           ├── SoapV2Classifications.java        ← Lista de <classification>
│           ├── SoapV2MessageContext.java         ← Lista de <property>
│           └── SoapV2MessageProperty.java        ← <key> + <value>
```

Los modelos JAXB no especifican `namespace` en sus anotaciones — el namespace se inyecta dinamicamente en tiempo de escritura via `NamespaceInjectingStreamWriter`. Esto permite que los namespaces de header y body sean configurables desde `application.yml` sin modificar las clases Java.

### Flujo de Construccion del Envelope

`buildEnvelope(request, props, traceId)` construye el envelope en 4 fases:

```
1. startEnvelope()     → Declaracion XML + prefijos + <soapenv:Envelope>
2. writeHeader()       → <soapenv:Header> + marshalling JAXB de SoapV2RequestHeader
3. writeBody()         → <soapenv:Body> + marshalling JAXB de TransmitirDocumentoRequest
4. writer.writeEndElement() → Cierre de </soapenv:Envelope>
```

#### Fase 1 — `startEnvelope()`

Registra los tres prefijos XML en el `XMLStreamWriter` y escribe el elemento raiz con sus namespace declarations:

```java
writer.setPrefix("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
writer.setPrefix("v2",      props.headerNamespace());   // ej: http://prueba.com/.../MessageFormat/V2.1
writer.setPrefix("v1",      props.bodyNamespace());     // ej: http://prueba.com/.../factory/adminDocs/V1.0

writer.writeStartElement(SOAP_ENVELOPE_NS, "Envelope");
writer.writeNamespace("soapenv", SOAP_ENVELOPE_NS);
writer.writeNamespace("v2",      props.headerNamespace());
writer.writeNamespace("v1",      props.bodyNamespace());
```

#### Fase 2 — `writeHeader()` y `NamespaceInjectingStreamWriter`

El header se escribe en dos pasos:

1. El `XMLStreamWriter` escribe `<soapenv:Header>` manualmente (elemento estructural del envelope SOAP).
2. El contenido del header (`<v2:requestHeader>...</v2:requestHeader>`) se genera via JAXB marshalling del objeto `SoapV2RequestHeader`.

`NamespaceInjectingStreamWriter` es un decorador de `XMLStreamWriter` que **sobrescribe el namespace** de cada `writeStartElement`/`writeEmptyElement` con un namespace configurable (en este caso, `props.headerNamespace()`). Como el writer ya tiene el prefijo `v2` registrado, los elementos se escriben como `<v2:systemId>`, `<v2:messageId>`, etc.

**Por que existe `NamespaceInjectingStreamWriter`:**

Los modelos JAXB (`SoapV2RequestHeader`, `SoapV2UserId`, etc.) no definen `namespace` en `@XmlRootElement` ni en `@XmlElement`. Si JAXB marshalleara directamente sobre el writer, los elementos se escribiran sin namespace (`<requestHeader>` en lugar de `<v2:requestHeader>`). El decorador intercepta cada `writeStartElement` y reescribe la llamada con el namespace correcto:

```java
// NamespaceInjectingStreamWriter.writeStartElement(String localName)
@Override
public void writeStartElement(String localName) throws XMLStreamException {
    delegate.writeStartElement(namespace, localName);  // namespace = props.headerNamespace()
}

// Si alguien intenta escribir un namespace distinto → XMLStreamException
private void assertNamespace(String namespaceURI) throws XMLStreamException {
    if (namespaceURI != null && !namespaceURI.isEmpty() && !namespaceURI.equals(this.namespace)) {
        throw new XMLStreamException("NamespaceInjectingStreamWriter: unexpected namespace '"
            + namespaceURI + "'. Expected '" + this.namespace + "'.");
    }
}
```

El `JAXB_FRAGMENT = true` evita que JAXB escriba `<?xml version="1.0"?>` dentro del envelope.

**Campos del header:**

| Elemento | Origen | Obligatorio |
|----------|--------|-------------|
| `<v2:systemId>` | `props.systemId()` | Si |
| `<v2:messageId>` | `traceId` | Si |
| `<v2:timestamp>` | `Instant.now().toString()` | Si |
| `<v2:userId>` | `props.userName()` + `props.userToken()` | Si |
| `<v2:userToken>` | `props.userToken()` | Solo si no es null/blank |
| `<v2:destination>` | `props.destinationName()` + `ns` + `operation` | Solo si destinationName no es null/blank |
| `<v2:classifications>` | `props.classifications()` | Solo si la lista no esta vacia |
| `<v2:messageContext>` | `props.messageContext()` | Solo si el mapa no esta vacio |

#### Fase 3 — `writeBody()`

Escribe `<soapenv:Body>` manualmente y luego marshallea `TransmitirDocumentoRequest` con JAXB. El body **no** usa `NamespaceInjectingStreamWriter` — los elementos del body se escriben sin namespace explicito, heredando el comportamiento por defecto del servicio SOAP destino.

**Campos del body:**

| Elemento | Origen |
|----------|--------|
| `<subTipoDocumental>` | `props.subTipoDocumental()` (ej: "Facturas") |
| `<nombreArchivo>` | `request.getFilename()` (default: "unknown") |
| `<archivo>` | `Base64.encode(request.getContent())` |
| `<metaData>` | `props.metaData()` — lista de `<tiposMetaData>` con `<nombre>`/`<valor>` |

### JAXBContext Dedicado

`SoapV2Mapper` mantiene su propio `JAXBContext` con **solo las clases V2**. Esto evita que los namespaces de los modelos V1 (`UploadFileRequest`, `UploadFileResponse` con namespace `http://example.com/fileservice`) se filtren en los elementos del body V2.

El contexto compartido en `SoapEnvelopeWrapper` se sigue usando para **unmarshalling de respuestas** (donde el namespace leak no ocurre por trabajar con DOM node-level).

### Ejemplo de XML Generado

Con la configuracion `application-dev.yml` y un archivo `doc.pdf` con contenido `{1,2,3}`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:v2="http://prueba.com/ents/SOI/MessageFormat/V2.1"
                  xmlns:v1="http://prueba.com/intf/factory/adminDocs/V1.0">
  <soapenv:Header>
    <v2:requestHeader>
      <v2:systemId>123</v2:systemId>
      <v2:messageId>trace-1</v2:messageId>
      <v2:timestamp>2026-05-06T10:15:30.123Z</v2:timestamp>
      <v2:userId>
        <v2:userName>test-user</v2:userName>
      </v2:userId>
    </v2:requestHeader>
  </soapenv:Header>
  <soapenv:Body>
    <transmitirDocumento>
      <subTipoDocumental>Facturas</subTipoDocumental>
      <nombreArchivo>doc.pdf</nombreArchivo>
      <archivo>AQID</archivo>
    </transmitirDocumento>
  </soapenv:Body>
</soapenv:Envelope>
```

### Parseo de Respuestas (`parseResponse`)

`parseResponse(String xml, String traceId)` delega en `SoapEnvelopeWrapper.unwrapResponse()` para:

1. Parsear el XML con `DocumentBuilderFactory` seguro (DOCTYPE deshabilitado, entidades externas bloqueadas — previene XXE).
2. Detectar **SOAP Faults**: si existe `<soapenv:Fault>`, lanza `ProcessingException` con el texto del fault.
3. Extraer el primer elemento hijo de `<soapenv:Body>` (que debe ser `<transmitirDocumentoResponse>`).
4. Unmarshallear ese nodo DOM directamente a `TransmitirDocumentoResponse` via `unmarshaller.unmarshal(Node)` — sin pasar por serializacion intermedia.
5. Mapear a `ExternalServiceResponse` con defaults seguros para campos nulos.

### Manejo de Errores y Recursos

- **try-finally**: `XMLStreamWriter` se cierra en bloque `finally` incluso si ocurre `XMLStreamException`, `JAXBException` o `RuntimeException`.
- **Dos capas de catch**: `XMLStreamException | JAXBException` para errores de marshalling conocidos, y `RuntimeException` para fallos inesperados (ej: `NullPointerException`).
- **traceId en logs y excepciones**: Cada error registra el `traceId` para correlacion con el request HTTP original.

### Integracion con SoapGatewayAdapter

```
SoapGatewayAdapter.transmitirDocumento(request)
  │
  ├── soapV2Mapper.buildEnvelope(request, v2Properties, traceId)
  │     └── String (XML completo listo para enviar por HTTP)
  │
  ├── executeSoapCall(webClientV2, soapEnvelope, ...)
  │     └── POST con Content-Type: text/xml + SOAPAction header
  │     └── Retry con backoff 500ms para errores reintentables
  │
  ├── soapV2Mapper.parseResponse(xml, traceId)
  │     └── ExternalServiceResponse
  │
  └── toFileUploadResult(response, attemptCount)
        └── FileUploadResult
```

### Configuracion (`application.yml`)

```yaml
app:
  soap:
    v2:
      endpoint: ${SOAP_V2_ENDPOINT:http://localhost:9000/soap/adminDocs}
      system-id: ${SOAP_V2_SYSTEM_ID:123}
      user-name: ${SOAP_V2_USER_NAME:775757775}
      header-namespace: ${SOAP_V2_HEADER_NS:http://prueba.com/ents/SOI/MessageFormat/V2.1}
      body-namespace: ${SOAP_V2_BODY_NS:http://prueba.com/intf/factory/adminDocs/V1.0}
      sub-tipo-documental: ${SOAP_V2_SUB_TIPO:Facturas}
      user-token: ${SOAP_V2_USER_TOKEN:}
      destination-name: ${SOAP_V2_DEST_NAME:bussinesdocs}
      destination-namespace: ${SOAP_V2_DEST_NS:http://prueba.com/.../Enlace/V1.0}
      destination-operation: ${SOAP_V2_DEST_OP:senddocs}
      soap-action: ${SOAP_V2_SOAP_ACTION:}
      classifications:
        - "http://prueba.com/clas/AppsUpdated"
      message-context:
        key01: "value01"
        key02: "value02"
      meta-data:
        xIdentificadorFisico: "65464904"
        xFilial: "prueba"
      timeout-seconds: 30
      retry-attempts: 3
```

---

## Stack Tecnologico

| Componente | Tecnologia |
|-----------|------------|
| **Framework** | Spring Boot 3.3.5 + WebFlux |
| **Lenguaje** | Java 21 |
| **Build** | Gradle 8.12.1 (Kotlin DSL) |
| **Base de datos (dev)** | H2 (embebida) |
| **Base de datos (prod)** | PostgreSQL |
| **Acceso a datos** | R2DBC + Spring Data R2DBC |
| **API REST externa** | WebClient (no bloqueante) |
| **Gateway SOAP** | WebClient + JAXB + DOM + StAX |
| **Gateway S3** | AWS SDK S3 Async (Netty) |
| **Observabilidad** | Micrometer Tracing (Brave) + Prometheus |
| **Validacion SOAP** | JAXB (jakarta.xml.bind) + DOM Parser seguro + StAX |
| **Testing** | JUnit 5, Mockito, Reactor Test, MockWebServer |
| **Calidad** | JaCoCo, PiTest (mutation testing) |
