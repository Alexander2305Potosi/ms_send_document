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

---

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas estrictamente separadas. La capa de dominio es Java puro sin dependencias de frameworks. La capa de infraestructura contiene los adaptadores concretos (R2DBC, REST, SOAP, S3). La comunicacion entre capas se realiza a traves de puertos (interfaces en `port/out`).

```
com.example.fileprocessor/
├── Application.java                              # @SpringBootApplication (excluye WebMvc)
│
├── domain/                                       # Capa de dominio
│   ├── entity/
│   │   ├── DocumentHistory.java                  # Record unificado: metadatos del documento + trazabilidad de envio
│   │   ├── DocumentStatus.java                   # Enum: SUCCESS, FAILURE
│   │   ├── ProductDocumentFile.java              # Record: documento obtenido de REST API
│   │   ├── ProductDocumentHistory.java           # Record: documento (21 campos, incluye productId, isZip, pais)
│   │   ├── ProductState.java                     # Constantes de state: PENDING, IN_PROGRESS, PROCESSED, FAILED, SYNCED
│   │   ├── FileUploadRequest.java                # Request para upload a gateway (SOAP/S3)
│   │   ├── FileUploadResult.java                 # Resultado de upload con status, errorCode, correlationId
│   │   ├── HomologationResult.java               # Resultado de homologacion origin/pais
│   │   └── ExternalServiceResponse.java          # Respuesta generica de servicio externo
│   ├── usecase/
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method base (procesa y descomprime ZIP en runtime)
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementacion SOAP
│   │   ├── S3DocumentProcessingUseCase.java         # Implementacion S3
│   │   ├── SyncDocumentsUseCase.java                # Sincroniza productos y documentos (sin validacion)
│   │   └── ProcessingResultCodes.java               # Constantes de codigos de error
│   ├── service/
│   │   └── RulesBussinesService.java              # Validacion: tamano maximo, patron filename
│   ├── util/
│   │   ├── ZipDecompressor.java                   # Descompresion de ZIP con inferencia de contentType
│   │   └── Base64Utils.java                       # Encoding/decoding seguro de Base64
│   ├── port/out/
│   │   ├── DocumentHistoryRepository.java        # Puerto unificado: CRUD de documentos, consulta por state, trazabilidad
│   │   ├── ProductRestGateway.java                # Puerto: API REST externa de productos
│   │   ├── RulesBussinesGateway.java              # Puerto: validacion de documentos
│   │   ├── S3Gateway.java                         # Puerto: envio a S3
│   │   ├── SoapGateway.java                       # Puerto: envio a SOAP
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
    │   │   ├── DocumentHistoryR2dbcAdapter.java   # Implementa DocumentHistoryRepository (tabla unificada)
    │   │   ├── HomologationR2dbcAdapter.java      # Implementa HomologationRepository (cache en memoria)
    │   │   ├── entity/
    │   │   │   ├── DocumentHistoryEntity.java      # @Entity @Table("historico_documentos") — tabla unificada
    │   │   │   ├── CategoryManualEntity.java       # @Entity @Table("categoria_manual")
    │   │   │   └── CountryHomologatedEntity.java  # @Entity @Table("pais_homologado")
    │   │   ├── mapper/
    │   │   │   └── DocumentHistoryMapper.java     # DocumentHistory <-> DocumentHistoryEntity
    │   │   └── repository/
    │   │       ├── DocumentHistoryRepository.java # R2dbcRepository<DocumentHistoryEntity, Long>
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
        │   └── SoapMapper.java                   # JAXB marshalling/unmarshalling + Base64
        └── xml/
            ├── SoapEnvelopeWrapper.java         # Envoltorio SOAP con parseo DOM seguro
            └── model/
                ├── UploadFileRequest.java       # @XmlRootElement para request SOAP
                └── UploadFileResponse.java      # @XmlRootElement para response SOAP
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
└── 001_create_documento_tables.sql  # DDL para la tabla unificada historico_documentos
```

---

## API Endpoints

### GET /api/v1/products

Procesa documentos pendientes desde la tabla `historico_documentos` en estado PENDING. Cada documento se obtiene de la API REST externa, se descomprime si es ZIP, se valida (nombre + tamano), y se envia al gateway (SOAP o S3).

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

Sincroniza productos y documentos desde la API REST externa hacia la base de datos. Por cada producto se listan sus documentos, se obtiene el contenido de cada uno desde la API REST, y se persiste en la tabla `historico_documentos` con `estado=SYNCED`.

**Headers:**
- `message-id`: (opcional) Trace ID para correlacion.

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
   POST /api/v1/products/sync  ──►   GET {productsPath}  (/api/products)
   ◄─────────────────────────────────
         [ProductResponse, ...]
               │
               ▼
3. SyncDocumentsUseCase.execute()
   ├── productRestGateway.getAllProducts()
   │     └── Flux<ProductDocumentHistory> (doc plano con productId, sin ProductHistory)
   │
   └── Por cada documento:
       ├── productRestGateway.getDocument(productId, documentId)
       │     └── GET {productDocumentsPath}/{documentId}
       │     └── Decodifica Base64 via Base64Utils.decodeSafe()
       ├── isZip se infiere de la extension del filename en el dominio
       ├── historyRepository.save()
           └── INSERT en tabla historico_documentos (estado=SYNCED)
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
4. historyRepository.findByState("PENDING")
   └── Filtra: estado=PENDING en tabla historico_documentos
        ▼
5. BD → Flux<DocumentHistory>
        │
        ▼
6. Por cada documento: processDocument(doc)
   ├── historyRepository.updateState(docId, "IN_PROGRESS", null)
   ├── productRestGateway.getDocument(doc.productId(), doc.documentId())
   │     └── GET {productDocumentsPath}/{docId}
   │     └── Decodifica Base64 via Base64Utils.decodeSafe()
   ├── Si isZip=true → ZipDecompressor.decompress() expande cada entrada
   ├── RulesBussinesGateway.validate(doc, true)  [patron nombre + tamano]
   │     └── Si no pasa → updateState(PROCESSED), skip (no se envia)
   ├── uploadDocument() → SoapGateway.send() o S3Gateway.send()
   │     └── Con reintentos automaticos + backoff
   ├── saveHistory(doc, result) → INSERT en historico_documentos (fila de trazabilidad)
   │     └── useCase = "SOAP" o "S3"
   │     └── retry = numero de intento actual
   └── historyRepository.updateState(docId, result.isSuccess ? "PROCESSED" : "FAILED", errorMessage)
        │
        ▼
7. Flux<FileUploadResult> → NDJSON stream al cliente
```

**Nota:** la descompresion ZIP se aplica tanto en procesamiento como en el dominio. La validacion de nombre y tamano solo se aplica en procesamiento.

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

### Tabla: historico_documentos (unificada)

Almacena tanto los metadatos del documento como la trazabilidad completa de cada intento de envio a los servicios externos (SOAP o S3). Las filas de metadatos tienen `caso_uso = NULL` y representan el estado actual del documento. Las filas de trazabilidad tienen `caso_uso` asignado y registran cada intento de procesamiento.

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
| `estado` | VARCHAR(100) | Estado del documento: PENDING / IN_PROGRESS / PROCESSED / FAILED / SYNCED |
| `version_contrato` | VARCHAR(50) | Version de contrato (nullable) |
| `mensaje_error` | TEXT | Mensaje de error si hubo fallo |
| `es_zip` | BOOLEAN | Si es un archivo ZIP comprimido |
| `nombre_zip_padre` | VARCHAR(255) | Si viene de un ZIP, nombre del ZIP padre (nullable) |
| `caso_uso` | VARCHAR(100) | Caso de uso del envio: SOAP, S3, o NULL para fila de metadatos |
| `resultado` | VARCHAR(50) | SUCCESS / FAILURE (NULL en fila de metadatos) |
| `codigo_error` | VARCHAR(50) | Codigo de error categorizado |
| `reintentos` | INTEGER | Numero de intento actual (default: 0) |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |
| `fecha_actualizacion` | TIMESTAMP | Fecha de ultima actualizacion |

### Tabla: categoria_manual

Almacena la homologacion de categorias de manuales. Se usa para resolver el `origin` de los documentos en el caso de uso SOAP usando busqueda contains.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador unico auto-generado |
| `categoria` | VARCHAR(255) | Codigo de categoria (ej: "manual_tecnico") |
| `descripcion_manual` | VARCHAR(500) | Descripcion legible (ej: "Manual Tecnico del Producto") |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

### Tabla: pais_homologado

Almacena la homologacion de paises. Se usa para resolver el `pais` de los documentos en el caso de uso SOAP.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador unico auto-generado |
| `pais` | VARCHAR(255) | Codigo de pais (ej: "AR", "CL") |
| `pais_homologado` | VARCHAR(255) | Nombre homologado del pais (ej: "Argentina", "Chile") |
| `fecha_creacion` | TIMESTAMP | Fecha de creacion del registro |

### Indices

```sql
-- historico_documentos
CREATE INDEX idx_historico_documento_id ON historico_documentos(id_documento);
CREATE INDEX idx_historico_estado ON historico_documentos(estado);
CREATE INDEX idx_historico_producto_id ON historico_documentos(id_producto);
CREATE INDEX idx_historico_documento_caso_uso ON historico_documentos(id_documento, caso_uso);

-- categoria_manual
CREATE INDEX idx_cat_manual_categoria ON categoria_manual(categoria);

-- pais_homologado
CREATE INDEX idx_pais_codigo ON pais_homologado(pais);
```
    
### DDL Completo

```sql
-- ============================================================================
-- Tabla: historico_documentos (unificada)
-- Almacena metadatos de documentos y trazabilidad de envios en una sola tabla.
-- Filas de metadatos: caso_uso IS NULL (representan el estado actual del documento).
-- Filas de trazabilidad: caso_uso IS NOT NULL (registran cada intento de envio).
-- ============================================================================
CREATE TABLE IF NOT EXISTS historico_documentos (
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
    resultado           VARCHAR(50),
    codigo_error        VARCHAR(50),
    reintentos          INTEGER         NOT NULL DEFAULT 0,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos(id_documento);
CREATE INDEX IF NOT EXISTS idx_historico_estado ON historico_documentos(estado);
CREATE INDEX IF NOT EXISTS idx_historico_producto_id ON historico_documentos(id_producto);
CREATE INDEX IF NOT EXISTS idx_historico_documento_caso_uso ON historico_documentos(id_documento, caso_uso);

-- ============================================================================
-- Tabla: categoria_manual
-- Homologacion de categorias de manuales para resolucion de origin en SOAP.
-- ============================================================================
CREATE TABLE IF NOT EXISTS categoria_manual (
    id                  BIGSERIAL       PRIMARY KEY,
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
    id              BIGSERIAL       PRIMARY KEY,
    pais            VARCHAR(255)    NOT NULL UNIQUE,
    pais_homologado VARCHAR(255)    NOT NULL,
    fecha_creacion  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pais_codigo ON pais_homologado(pais);
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
-- Ver todos los registros (metadatos y trazabilidad)
SELECT * FROM historico_documentos ORDER BY fecha_creacion DESC;

-- Ver solo metadatos de documentos (estado actual)
SELECT * FROM historico_documentos WHERE caso_uso IS NULL;

-- Ver trazabilidad de un documento especifico
SELECT * FROM historico_documentos WHERE id_documento = 'doc-123' AND caso_uso IS NOT NULL;

-- Ver solo envios por SOAP
SELECT * FROM historico_documentos WHERE caso_uso = 'SOAP';

-- Ver envios con reintentos > 0
SELECT * FROM historico_documentos WHERE reintentos > 0 ORDER BY fecha_creacion DESC;

-- Ver documentos pendientes de procesamiento
SELECT * FROM historico_documentos WHERE estado = 'PENDING' AND caso_uso IS NULL;

-- Ver documentos fallidos
SELECT * FROM historico_documentos WHERE estado = 'FAILED' AND caso_uso IS NULL;

-- Ver documentos descomprimidos de un ZIP
SELECT * FROM historico_documentos WHERE nombre_zip_padre = 'documents.zip';

-- Contar envios por caso de uso y resultado
SELECT caso_uso, resultado, COUNT(*) FROM historico_documentos WHERE caso_uso IS NOT NULL GROUP BY caso_uso, resultado;

-- Ver ultimo reintento de cada documento por caso de uso
SELECT h.*
FROM historico_documentos h
JOIN (
  SELECT id_documento, caso_uso, MAX(fecha_creacion) as max_fecha
  FROM historico_documentos
  WHERE caso_uso IS NOT NULL
  GROUP BY id_documento, caso_uso
) latest ON h.id_documento = latest.id_documento AND h.caso_uso = latest.caso_uso AND h.fecha_creacion = latest.max_fecha;
```

---

## Descompresion de archivos ZIP

`ZipDecompressor.decompress()` expande documentos ZIP. Se aplica durante la sincronizacion (sync) y el procesamiento.

### Inferencia de isZip

`isZip` se infiere de la extension del archivo en la capa de dominio (`ProductDocumentHistory.isZip()`).

### Comportamiento durante Sync

| Escenario | Resultado |
|-----------|-----------|
| Documento normal (`isZip=false`) | Se guarda tal cual en `historico_documentos` |
| Documento ZIP (`isZip=true`) | Primero se guarda el ZIP con `nombre_zip_padre=NULL`, luego cada archivo expandido se guarda con `nombre_zip_padre=filename_del_zip` |

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

Un documento ZIP con `id_documento=doc-1` y `nombre=documents.zip` que contiene `test.pdf` y `data.csv`:

```
ZIP: doc-1/documents.zip (isZip=true)
  ├── documents.zip  → id_documento="doc-1", nombre_zip_padre=null, isZip=true
  ├── test.pdf       → id_documento="doc-1/test.pdf", nombre_zip_padre="documents.zip", isZip=false
  └── data.csv       → id_documento="doc-1/data.csv", nombre_zip_padre="documents.zip", isZip=false
```

---

## Estados de Documentos (ProductState)

```java
public final class ProductState {
    public static final String PENDING     = "PENDING";     // Esperando procesamiento
    public static final String IN_PROGRESS = "IN_PROGRESS"; // En procesamiento actual
    public static final String PROCESSED   = "PROCESSED";   // Enviado exitosamente
    public static final String FAILED      = "FAILED";      // Agoto reintentos o fallo permanente
    public static final String SYNCED      = "SYNCED";      // Sincronizado desde REST API, listo para procesar
}
```

### Transiciones de State

```
               sync
                 │
                 ▼
              [SYNCED]          ← sincronizado, en tabla historico_documentos
                 │
                 │  (luego estado=PENDING)
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
    (envio OK)     (reintentos agotados)
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
saveHistory() → INSERT en historico_documentos (resultado=SUCCESS, reintentos=0, caso_uso=SOAP/S3)
updateState()  → Actualiza fila de metadatos: estado=PROCESSED
Stream: {"success":true, "status":"SUCCESS"}
```

### 2. Error en Gateway (con reintentos)
```
uploadDocument() → exception
handleUploadError() → getRetryCount() desde historico_documentos
saveHistory() → INSERT en historico_documentos (resultado=FAILURE, reintentos=N, caso_uso=SOAP/S3)
updateState():
  - Si reintentos < 3: estado=PENDING (se reintentara)
  - Si reintentos >= 3: estado=FAILED
Stream: {"success":false, "status":"FAILURE", "errorCode":"GATEWAY_TIMEOUT", "retry":3}
```

### 3. Documento ZIP
```
Sync/Processing: isZip=true → ZipDecompressor.decompress()
           → Cada archivo expandido se procesa independientemente
           → Se guardan en tabla historico_documentos con estado=PENDING o se envian al gateway
```

### 4. Error en Descompresion ZIP
```
ZipDecompressor.decompress() → ProcessingException(INVALID_ZIP)
saveHistory() → INSERT en historico_documentos (errorCode=INVALID_ZIP)
documento no se guarda / no se procesa
```

### 5. Error de Base64
```
Base64Utils.decodeSafe() → InvalidBase64Exception(INVALID_BASE64)
saveHistory() → INSERT en historico_documentos (errorCode=INVALID_BASE64)
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

Cada documento procesado deja un registro en `historico_documentos` con el `caso_uso` que lo envio (SOAP o S3), lo que permite tracking por caso de uso y analisis de reintentos.

### Estructura de la tabla unificada

La tabla `historico_documentos` contiene dos tipos de filas:

- **Filas de metadatos** (`caso_uso IS NULL`): Representan el estado actual del documento. Se crean durante la sincronizacion y se actualizan durante el procesamiento.
- **Filas de trazabilidad** (`caso_uso IS NOT NULL`): Registran cada intento de envio a un gateway. Son append-only para auditoria.

### Campos clave

- **`caso_uso`**: Identifica el gateway usado ("SOAP" o "S3"). NULL en filas de metadatos.
- **`reintentos`**: Numero de intento actual (0 = primer intento, 1 = primer reintento, etc.). Se consulta `getRetryCount()` desde `historico_documentos` antes de cada intento.
- **`id_documento`**: Incluye la ruta cuando el documento viene de un ZIP (ej: `doc-1/test.pdf`).

### Flujo de Persistencia

```
uploadDocument() → FileUploadResult
        │
        ▼
handleUploadSuccess() o handleUploadError()
        │
        ▼
DocumentHistory (
    null,                          // id (auto-generado)
    document.documentId(),        // id_documento
    document.productId(),         // id_producto
    ...metadata fields...,        // nombre, propietario, ruta, etc.
    implementationName(),         // caso_uso = "SOAP" o "S3"
    isSuccess ? "SUCCESS" : "FAILURE", // resultado
    errorCode,                    // codigo_error
    errorMessage,                // mensaje_error
    retryCount,                  // reintentos = numero de intento
    now,                         // fecha_creacion
    now                          // fecha_actualizacion
)
        │
        ▼
historyRepository.save(record)  → INSERT en historico_documentos
historyRepository.updateState() → UPDATE en fila de metadatos (caso_uso IS NULL)
```

---

## Template Method Pattern

El patron se implementa en `AbstractDocumentProcessingUseCase`:

```
AbstractDocumentProcessingUseCase
│
├── executePendingDocuments()           ← FINAL (template method)
│   ├── historyRepository.findByState("PENDING")  → BD (tabla historico_documentos)
│   ├── historyRepository.updateState(docId, "IN_PROGRESS", null)
│   ├── Por cada documento:
│   │   ├── productRestGateway.getDocument(productId, docId) → REST externa
│   │   ├── toProductDocument(file) → ProductDocumentHistory
│   │   ├── Si isZip=true → ZipDecompressor.decompress() expande entradas
│   │   ├── documentValidator.validate(doc, true) → validacion nombre + tamano
│   │   ├── uploadDocument()       → ABSTRACT (SOAP o S3)
│   │   ├── handleUploadSuccess() o handleUploadError() → INSERT trazabilidad + UPDATE metadatos
│   │   └── historyRepository.updateState(docId, estado, mensaje_error)
│   │
│
├── uploadDocument()                     ← ABSTRACT
│   └── buildFileUploadRequest()         ← helper protegido
│
├── handleUploadError(Throwable)          ← helper protegido (reusable por subclases)
│
├── SoapDocumentProcessingUseCase
│   └── uploadDocument() → HomologationRepository.resolve() → SoapGateway.send()
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
| **Gateway SOAP** | WebClient + JAXB + DOM |
| **Gateway S3** | AWS SDK S3 Async (Netty) |
| **Observabilidad** | Micrometer Tracing (Brave) + Prometheus |
| **Validacion SOAP** | JAXB (jakarta.xml.bind) + DOM Parser seguro |
| **Testing** | JUnit 5, Mockito, Reactor Test, MockWebServer |
| **Calidad** | JaCoCo, PiTest (mutation testing) |
