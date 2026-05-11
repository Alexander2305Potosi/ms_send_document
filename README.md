# File Processor Service

Microservicio reactivo basado en **Spring WebFlux + R2DBC** que gestiona el procesamiento resiliente de documentos. Obtiene productos con sus documentos asociados desde una API REST externa, los persiste en una base de datos PostgreSQL (producción) o H2 (desarrollo), valida reglas de negocio, descomprime archivos ZIP en caliente y los envía a gateways externos (**SOAP** o **AWS S3**) con una política estricta de reintentos y trazabilidad atómica.

---

## Tabla de Contenidos

1. [Arquitectura (Clean Architecture)](#arquitectura-clean-architecture)
2. [API Endpoints](#api-endpoints)
3. [Flujo de Datos](#flujo-de-datos)
4. [Base de Datos](#base-de-datos)
5. [Descompresión de archivos ZIP](#descompresion-de-archivos-zip)
6. [Estados de Documentos (ProductState)](#estados-de-documentos-productstate)
7. [Reglas de Negocio (RulesBussinesService)](#reglas-de-negocio-rulesbussinesservice)
8. [Escenarios de Procesamiento](#escenarios-de-procesamiento)
9. [Códigos de Error](#codigos-de-error)
10. [Trazabilidad de Envíos](#trazabilidad-de-envios)
11. [Template Method Pattern](#template-method-pattern)
12. [Perfiles de Ejecución](#perfiles-de-ejecucion)
13. [Variables de Entorno](#variables-de-envorno)
14. [Compilación y Ejecución](#compilacion-y-ejecucion)
15. [Ejemplos de curl](#ejemplos-de-curl)
16. [Excepciones](#excepciones)
17. [Testing](#testing)

---

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con capas estrictamente separadas para garantizar mantenibilidad y testabilidad. La capa de dominio es Java puro (POJOs/Records) sin dependencias de frameworks. La infraestructura contiene los adaptadores concretos (R2DBC, REST, SOAP, S3). La comunicación entre capas se realiza a través de puertos (interfaces en `port/out`).

```text
com.example.fileprocessor/
├── Application.java                              # @SpringBootApplication (excluye WebMvc)
│
├── domain/                                       # Capa de dominio (Java Puro)
│   ├── entity/
│   │   ├── Document.java                       # Record: metadatos del documento + estado actual
│   │   ├── ProductDocumentFile.java            # Record: documento obtenido de REST API
│   │   ├── ProductDocumentHistory.java         # Record: documento procesable (con metadatos de producto)
│   │   ├── ProductState.java                   # Constantes de estado: PENDING, IN_PROGRESS, PROCESSED, FAILED
│   │   ├── FileUploadRequest.java              # Request para upload a gateway (SOAP/S3)
│   │   ├── FileUploadResponse.java             # Resultado de upload con status, errorCode, message
│   │   ├── FinalizeProcessingCommand.java      # Parameter Object: encapsula datos para finalización atómica
│   │   └── ExternalServiceResponse.java        # Respuesta genérica de servicio externo
│   ├── usecase/
│   │   ├── AbstractDocumentProcessingUseCase.java  # Template Method: Pipeline de Resiliencia y transaccionalidad
│   │   ├── SoapDocumentProcessingUseCase.java       # Implementación SOAP Unificada
│   │   ├── S3DocumentProcessingUseCase.java         # Implementación S3
│   │   ├── SyncDocumentsUseCase.java                # Sincroniza productos y metadatos desde API REST
│   │   └── ProcessingResultCodes.java               # Enum: Fuente única de verdad para errores
│   ├── service/
│   │   └── RulesBussinesService.java              # Lógica de validación (tamaño, patrón de nombre)
│   ├── util/
│   │   ├── ZipDecompressor.java                   # Descompresión de ZIP con inferencia de contentType
│   │   ├── Base64Utils.java                       # Encoding/decoding seguro de Base64
│   │   └── MimeTypeUtil.java                      # Resolución de tipos MIME
│   ├── port/out/
│   │   ├── DocumentPersistenceGateway.java       # Puerto: Fachada unificada para persistencia
│   │   ├── DocumentRepository.java               # Puerto: Gestión de metadatos de documentos
│   │   ├── DocumentHistoryRepository.java        # Puerto: Auditoría y trazabilidad
│   │   ├── ProductRestGateway.java               # Puerto: Consumo API REST externa
│   │   ├── RulesBussinesGateway.java             # Puerto: Contrato de validación
│   │   ├── S3Gateway.java                        # Puerto: Envío a S3
│   │   └── SoapGateway.java                      # Puerto: Envío a SOAP
│   └── exception/
│       ├── DomainException.java                  # Base abstracta (RuntimeException + errorCode)
│       └── ProcessingException.java              # Error general de procesamiento en el pipeline
│
├── application/                                   # Capa de aplicación
│   └── service/config/
│       └── DomainConfig.java                      # @Configuration: Definición de beans inyectando puertos
│
└── infrastructure/                                # Capa de infraestructura
    ├── config/
    │   └── ProcessorsProperties.java              # Propiedades unificadas (límites, reintentos)
    ├── drivenadapters/
    │   ├── DocumentPersistenceAdapter.java        # Adaptador Fachada que orquesta transacciones R2DBC
    │   ├── r2dbc/                                 # Adaptadores reactivos R2DBC
    │   │   ├── DocumentR2dbcAdapter.java          # Implementación DocumentRepository
    │   │   ├── DocumentHistoryR2dbcAdapter.java   # Implementación DocumentHistoryRepository
    │   │   ├── entity/                            # Entidades mapeadas a tablas SQL
    │   │   ├── mapper/                            # Mapeadores Entidad-Dominio
    │   │   └── repository/                        # Repositorios Spring Data R2DBC
    │   ├── restclient/
    │   │   └── ProductRestGatewayAdapter.java     # Implementa cliente WebClient para API REST
    │   ├── soap/
    │   │   └── SoapGatewayAdapter.java            # Cliente HTTP (WebClient) para peticiones SOAP
    │   └── aws/
    │       └── S3GatewayAdapter.java              # Cliente asíncrono para AWS S3
    └── entrypoints/rest/
        ├── ProductRoutes.java                     # Router function (GET /products, GET /products/sync)
        └── handler/ProductHandler.java            # Handler de orquestación HTTP
```

---

## API Endpoints

### GET `/api/v1/products/sync`

Sincroniza metadatos de documentos desde la API REST externa hacia la base de datos local. Solo registra la existencia y metadatos, no procesa los binarios.

- **Response:** HTTP 200 (fire-and-forget)
```json
{"status":"OK","message":"Document sync initiated"}
```

### GET `/api/v1/products`

Ejecuta el pipeline de procesamiento sobre los documentos en estado `PENDING` del **día actual**. El sistema descarga, valida, descomprime y envía según el procesador seleccionado.

- **Query Params:**
  - `processor`: `soap` (default) | `s3`
- **Response:** `application/x-ndjson` (Stream reactivo)
```json
{"correlationId":"corr-123","status":"SUCCESS","success":true,"processedAt":"2026-05-10T20:15:00Z","errorCode":null,"attemptCount":1}
```

---

## Flujo de Datos

### Flujo de Sincronización (POST/GET Sync)

1. Se obtienen todos los productos desde la API REST.
2. Por cada producto, se listan sus documentos.
3. Se persiste cada documento en la tabla `documentos` con estado `PENDING`.

### Flujo de Procesamiento

```mermaid
sequenceDiagram
    participant Worker
    participant DB as Database
    participant ExtAPI as External API
    participant Zip as ZipDecompressor
    participant Rules as RulesService
    participant Gateway as SOAP / S3
    
    Worker->>DB: findByState("PENDING")
    Worker->>DB: updateState(ID, "IN_PROGRESS") [Lock]
    
    alt Lock Acquired
        Worker->>ExtAPI: getDocument(Base64)
        alt isZip == true
            Worker->>Zip: decompress()
        end
        Worker->>Rules: Validate (Size, Name)
        alt Valid
            Worker->>Gateway: Upload File
            Gateway-->>Worker: Success / Failure
        end
        Worker->>DB: finalizeProcessing() [Transactional]
        Note right of DB: update `documentos`<br/>insert `historico_documentos`
    end
```

---

## Base de Datos

### Tabla: `documentos`

Almacena el estado transaccional actual del documento.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador único |
| `id_documento` | VARCHAR(100) | ID en sistema externo |
| `id_producto` | VARCHAR(100) | ID del producto padre |
| `nombre` | VARCHAR(255) | Nombre del archivo |
| `estado` | VARCHAR(100) | PENDING, IN_PROGRESS, PROCESSED, FAILED |
| `mensaje_error` | TEXT | Último mensaje de error detallado |
| `reintentos` | INTEGER | Intentos técnicos acumulados |
| `fecha_creacion` | TIMESTAMP | Fecha de registro |
| `fecha_actualizacion` | TIMESTAMP | Fecha de último cambio de estado |

### Tabla: `historico_documentos` (Auditoría)

Registra cada intento de procesamiento de manera inmutable.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador único |
| `documento_id` | BIGINT (FK) | Referencia a `documentos(id)` |
| `nombre_archivo` | VARCHAR(255) | Nombre del archivo (útil en ZIPs) |
| `operacion` | VARCHAR(50) | SOAP, S3, SYNC |
| `resultado` | VARCHAR(50) | SUCCESS / FAILURE |
| `codigo_error` | VARCHAR(50) | Categoría del error (TIMEOUT, SIZE, etc.) |
| `reintentos` | INTEGER | Número de intento de este registro |
| `fecha_inicio` | TIMESTAMP | Inicio de la operación |
| `fecha_fin` | TIMESTAMP | Fin de la operación |

---

## Descompresión de archivos ZIP

El componente `ZipDecompressor` expande archivos comprimidos en memoria durante el pipeline de procesamiento:

1. **Detección**: Si la extensión es `.zip`, se marca como `es_zip = true`.
2. **Expansión**: El flujo emite un registro por cada entrada dentro del ZIP.
3. **Trazabilidad**: Cada entrada genera su propio registro en `historico_documentos` vinculado al mismo `documento_id` padre, permitiendo ver el detalle de éxito/fallo de cada archivo individual contenido en el paquete.

---

## Estados de Documentos (ProductState)

- **PENDING**: Listo para ser tomado por el worker.
- **IN_PROGRESS**: Bloqueo optimista para evitar doble procesamiento concurrente.
- **PROCESSED**: Procesamiento finalizado (éxito o skip por reglas de negocio).
- **FAILED**: Agotó los 3 reintentos permitidos o error fatal irrecuperable.

---

## Reglas de Negocio (RulesBussinesService)

El componente `RulesBussinesService` actúa como un **filtro reactivo (Guard Clause)** dentro del pipeline de procesamiento (`AbstractDocumentProcessingUseCase`). Su responsabilidad principal es prevenir que documentos inválidos alcancen los gateways externos (SOAP/S3), ahorrando ancho de banda y tiempos de CPU.

### 1. Inyección Dinámica y Binding de Configuración
La instanciación del servicio no utiliza `@Service` a nivel de clase, sino que se inyecta dinámicamente vía `@Bean` en la clase `DomainConfig`. Esto permite crear instancias independientes del validador para cada caso de uso con límites distintos (Ej: SOAP permite 10MB, S3 permite 50MB), leyéndolo directamente desde las propiedades de Spring (`ProcessorsProperties`).

El binding desde `application.yml` se estructura de la siguiente forma:
```yaml
app:
  processors:
    soap:
      max-file-size-bytes: 10485760        # 10 MB exactos
      filename-pattern: ".*\\.(pdf|docx|txt)$"
```
Durante la instanciación, la expresión regular (Regex) de `filename-pattern` es compilada internamente en memoria usando `java.util.regex.Pattern.compile()` para garantizar un rendimiento óptimo de coincidencia en operaciones de streaming.

### 2. Matriz de Validación y Ejecución Tardía (Lazy Evaluation)
El servicio distingue dos fases en las que actúa, permitiendo un nivel de rigurosidad adaptativo:
- **Fase de Sincronización (Sync)**: Únicamente valida el `filenamePattern`. No valida el tamaño, ya que en la respuesta de la API REST externa el tamaño base64 no refleja el tamaño del archivo binario decodificado.
- **Fase de Procesamiento**: Valida de manera estricta tanto el patrón del nombre como el peso real del binario (ya decodificado en memoria).

La ejecución de la regla es reactivamente perezosa mediante `Mono.defer(...)`, garantizando que la evaluación matemática y Regex se apliquen **solo en el momento en que un hilo del Schedulers** asume la ejecución del bloque, respetando el control de concurrencia y Backpressure de Project Reactor.

### 3. Mecánica de Bypass (Skip Logic) y Flujo de Interrupciones
Cuando una regla falla, el diseño evita usar condicionales `if-else` en el pipeline. En su lugar, el `RulesBussinesService` lanza un `Mono.error(new ProcessingException(...))`.

```java
// Ejemplo interno de validación
if (doc.getSize() > maxFileSizeBytes) {
    return Mono.error(new ProcessingException(ProcessingResultCodes.SIZE_EXCEEDED.name(), ...));
}
```

Esta excepción rompe el flujo normal y es atrapada por el operador `.onErrorResume` en el `AbstractDocumentProcessingUseCase`. Aquí es donde ocurre la magia de la "Lógica de Salto" (Skip Logic):
1. **Atrapado Controlado**: Al identificar que el código de error (`SIZE_EXCEEDED` o `PATTERN_MISMATCH`) pertenece a un fallo **determinista** (de Negocio), el sistema decide no reintentar. Un archivo de 20MB nunca pasará una regla de 10MB.
2. **Commit Atómico Diferenciado**: En lugar de marcar el documento como `FAILED`, el sistema lo marca en la base de datos como **`PROCESSED`**. Esto saca permanentemente el archivo del "pool" de documentos pendientes, simulando que su ciclo de vida terminó correctamente.
3. **Auditoría Honesta**: Aunque el estado en la tabla `documentos` sea `PROCESSED`, en la tabla transaccional `historico_documentos` se inserta un registro con resultado **`FAILURE`** y el respectivo `codigo_error`. Así, el negocio sabe exactamente por qué no se envió al sistema externo.

---

## Escenarios de Procesamiento

1. **Caso Ideal**: Sincronización -> Procesamiento -> Éxito -> Estado PROCESSED.
2. **Error Técnico (Reintentable)**: Fallo de conexión o timeout -> El estado vuelve a PENDING -> Se incrementa `reintentos` -> El worker lo retomará.
3. **Error de Negocio**: Archivo demasiado grande -> No se envía -> Estado PROCESSED con detalle `SIZE_EXCEEDED`.
4. **Fallo Crítico**: Base64 corrupto o ZIP inválido -> Estado FAILED directamente.

---

## Códigos de Error (ProcessingResultCodes)

- `SUCCESS`: Operación exitosa.
- `GATEWAY_TIMEOUT`: Tiempo de espera agotado (Reintentable).
- `SIZE_EXCEEDED`: Archivo excede el límite permitido.
- `PATTERN_MISMATCH`: Nombre de archivo no cumple con el formato.
- `INVALID_BASE64`: Error al decodificar el contenido.
- `EMPTY_CONTENT`: El documento no tiene contenido binario.

---

## Trazabilidad de Envíos

Toda operación que afecte el estado de un documento es **atómica y transaccional**. Gracias al uso de `TransactionalOperator`, garantizamos que:
1. Se actualice el estado y contador de reintentos en `documentos`.
2. Se inserte un registro detallado en `historico_documentos`.

Si ocurre un error en la persistencia, ambas acciones se revierten, manteniendo la integridad de la base de datos.

---

## Template Method Pattern

La lógica central reside en `AbstractDocumentProcessingUseCase`, la cual define el "esqueleto" del proceso:
- Limpieza de documentos estancados.
- Búsqueda de pendientes.
- Bloqueo y descarga.
- Validación y Envío.
- Finalización Transaccional.

Las implementaciones concretas (`SoapDocumentProcessingUseCase`, `S3DocumentProcessingUseCase`) solo proveen la lógica específica de envío al gateway correspondiente.

---

## Perfiles de Ejecución

- **dev**: H2 en memoria, logs DEBUG, timeouts cortos.
- **prod**: PostgreSQL, logs WARN, configuración productiva.
- **s3**: Habilita específicamente el soporte para AWS S3.

---

## Variables de Envornó

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`: Conexión PostgreSQL.
- `PROCESSOR_SOAP_ENDPOINT`: URL del servicio SOAP destino.
- `PROCESSOR_REST_ENDPOINT`: URL de la API de productos externa.
- `AWS_REGION`, `AWS_S3_BUCKET`: Datos de configuración S3.

---

## Compilación y Ejecución

```bash
# Compilar y ejecutar tests
./gradlew clean build

# Iniciar aplicación (Perfil dev)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## Ejemplos de curl

```bash
# Sincronizar
curl http://localhost:8080/api/v1/products/sync

# Procesar SOAP
curl "http://localhost:8080/api/v1/products?processor=soap"
```

---

## Excepciones

Se utiliza una jerarquía basada en `DomainException` para capturar errores de negocio y técnicos. Todas las excepciones capturadas en el pipeline se traducen a un código de error estándar en la tabla de auditoría, evitando que el flujo reactivo se rompa inesperadamente.

---

## Testing

El proyecto cuenta con una cobertura robusta:
- **Unitarios**: Mockito + StepVerifier para el pipeline.
- **Integración**: Testcontainers/H2 para validar el esquema R2DBC y los repositorios.
- **Mocks**: MockWebServer para simular fallos en las APIs externas.

---

## Stack Tecnológico

- **Java 21**
- **Spring Boot 3.3.x (WebFlux)**
- **R2DBC** (PostgreSQL / H2)
- **AWS SDK v2** (S3 Async)
- **Lombok**
- **JAXB** (SOAP Marshalling)
