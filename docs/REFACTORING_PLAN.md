# Plan Exhaustivo de Refactorización y Estandarización

Este documento detalla el plan arquitectónico para alinear el modelo de datos y la capa de persistencia basándose en los payloads del Centro de Productos/Sistema Origen y la estructura limpia (Clean Architecture) implementada en el microservicio.

## 1. Análisis y Limpieza del Modelo de Datos (Tablas)

Basado en el payload JSON proporcionado y las entidades actuales (`ProductDataDocument`, `ProductDataDocumentFile`, `DownloadDTO`), se propone la siguiente optimización de campos para eliminar redundancias y mejorar la claridad.

### A. Entidad Principal: `Document` → tabla `documentos`

> **Resultado de Auditoría:** Campos sin relevancia eliminados definitivamente. Solo permanecen los necesarios para el flujo de negocio y auditoría.

| Campo Dominio (`Document.java`) | Columna DB | Tipo | Estado | Acción / Razón |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `id` | `Long` | **MANTENER** | PK autoincremental de la tabla. |
| `documentId` | `id_documento` | `VARCHAR(50)` | **MANTENER** | ID del sistema origen (ej. 'Doc123'). |
| `productId` | `id_producto` | `VARCHAR(50)` | **MANTENER** | ID del producto al que pertenece. |
| `name` | `nombre` | `VARCHAR(255)` | **MANTENER** | Nombre del archivo/documento. |
| `docKey` | `clave_documento` | `VARCHAR(500)` | **MANTENER** | Ruta para S3. Reemplaza a `path`. |
| `state` | `estado` | `VARCHAR(50)` | **MANTENER** | Estados: `PENDING`, `IN_PROGRESS`, `PROCESSED`, `FAILED`. |
| `versionContract` | `version_contrato` | `VARCHAR(20)` | **MANTENER** | Versión del contrato (requerida en homologación SOAP). |
| `errorMessage` | `mensaje_error` | `VARCHAR(1000)` | **MANTENER** | Mensaje del último error (evita JOIN al histórico para consultas rápidas). |
| `isZip` | `es_zip` | `BOOLEAN` | **MANTENER** | Crítico para el filtro pre-descarga. |
| `parentZipName` | `nombre_zip_padre` | `VARCHAR(255)` | **MANTENER** | Nombre del ZIP padre si fue extraído. |
| `useCase` | `caso_uso` | `VARCHAR(50)` | **MANTENER** | Segregación SOAP/S3. |
| `retryCount` | `reintentos` | `INT` | **MANTENER** | Contador de reintentos (máx. 3). |
| `createdAt` | `fecha_creacion` | `TIMESTAMP` | **MANTENER** | Fecha de inserción del registro. |
| ~~`active`~~ | ~~`activo`~~ | - | **ELIMINAR** | Redundante con `estado`. |
| ~~`owner`~~ | ~~`propietario`~~ | - | **ELIMINAR** | Redundante con `id_producto`. |
| ~~`path`~~ | ~~`ruta`~~ | - | **ELIMINAR** | Reemplazado completamente por `clave_documento`. |
| ~~`updatedAt`~~ | ~~`fecha_actualizacion`~~ | - | **ELIMINAR** | Derivable de `historico_documentos.fecha_fin`. |

**Visualización del Cambio en el Código:**

```java
// [CÓDIGO ANTES] - Con campos redundantes
public class Document {
    private Long id;
    private String documentId;
    private String productId;
    private Boolean active;      // ← ELIMINAR: redundante con state
    private String docKey;
    private String name;
    private String owner;        // ← ELIMINAR: redundante con productId
    private String path;         // ← ELIMINAR: reemplazado por docKey
    private String state;
    private String versionContract;
    private String errorMessage;
    private boolean isZip;
    private String parentZipName;
    private String useCase;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt; // ← ELIMINAR: derivable del histórico
    private Integer retryCount;
}
```

```java
// [CÓDIGO DESPUÉS] - Limpio, sin redundancias
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Document {
    private Long id;
    private String documentId;       // ID del sistema origen
    private String productId;        // ID del producto
    private String docKey;           // Ruta S3
    private String name;             // Nombre del documento
    private String state;            // Estado de procesamiento
    private String versionContract;
    private String errorMessage;     // Último mensaje de error
    private boolean isZip;
    private String parentZipName;
    private String useCase;          // Segregación SOAP/S3
    private LocalDateTime createdAt;
    private Integer retryCount;

    public int getRetryCountSafe() {
        return retryCount != null ? retryCount : 0;
    }
}
```

### B. Modelo Transitorio en Memoria: `ProductDataDocumentFile` (Binarios)

Esta clase se encarga exclusivamente de transitar el contenido descargado hacia el siguiente paso (ej. SOAP/S3). **El archivo vive únicamente en la memoria RAM (como `byte[]`) durante el flujo reactivo y NO se persiste en la base de datos**, ahorrando inmensos costos de almacenamiento.

| Campo Actual | Estado Propuesto | Nuevo Tipo | Origen en JSON | Razón del Cambio / Acción |
| :--- | :--- | :--- | :--- | :--- |
| `id` | **MANTENER** | `String` | `id` | ID referencial al documento original. |
| `title` | **MANTENER** | `String` | `title` | Nombre del archivo. |
| `status` | **ELIMINAR** | - | - | El estado debe manejarse en la tabla principal de metadatos, no en la del binario. |
| `parentId` | **MANTENER** | `String` | `parentId` | Agrupador lógico. |
| `contentType` | **MANTENER** | `String` | `contentType` | Tipo MIME ("application/vnd..."). |
| `extension` | **MANTENER** | `String` | `extension` | Tipo de archivo ("docx"). |
| `size` | **MANTENER** | `Long` | `size` | Validaciones de tamaño. |
| `fileContent` | **ACTUALIZAR** | `byte[]` | `fileContent` | **CRÍTICO:** Mapear a `byte[]` para optimizar memoria durante el tránsito. **No se persiste en Base de Datos.** |

**Visualización del Cambio en el Código:**

```java
// [CÓDIGO ANTES] - Entidad Original
public class ProductDataDocumentFile {
    private String id;
    private String title;
    private String status;
    private String parentId;
    private String contentType;
    private String extension;
    private byte[] fileContent;
    private Long size;
}
```

```java
// [CÓDIGO DESPUÉS] - Modelo de Dominio Transitorio (NO es una tabla)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductDataDocumentFile {
    private String id;
    private String title;
    private String parentId;
    private String contentType;
    private String extension;
    private Long size;
    private byte[] fileContent;
    // Eliminado: status (se maneja en ProductDataDocument)
}
```

### C. Scripts DDL Iniciales (Base de Datos)

> **Corrección de Auditoría:** La tabla principal es `documentos` (no `productos`). Las columnas usan nombres en **español** conforme a la convención ya establecida en el código.

```sql
-- ==========================================
-- 1. TABLA PRINCIPAL: DOCUMENTOS (Metadatos de Proceso)
-- ==========================================

CREATE SEQUENCE seq_documentos START WITH 1 INCREMENT BY 1;

CREATE TABLE documentos (
    id                BIGINT       DEFAULT nextval('seq_documentos') PRIMARY KEY,
    id_documento      VARCHAR(50)  NOT NULL,      -- ID del sistema origen
    id_producto       VARCHAR(50)  NOT NULL,      -- ID del producto
    clave_documento   VARCHAR(500),               -- Ruta S3 (reemplaza 'ruta')
    nombre            VARCHAR(255),               -- Nombre del archivo
    estado            VARCHAR(50)  NOT NULL DEFAULT 'PENDING', -- PENDING|IN_PROGRESS|PROCESSED|FAILED
    version_contrato  VARCHAR(20),                -- Necesaria para homologación SOAP
    mensaje_error     VARCHAR(1000),              -- Mensaje del último error (consultas rápidas)
    es_zip            BOOLEAN      DEFAULT FALSE,
    nombre_zip_padre  VARCHAR(255),               -- Nombre del ZIP padre si aplica
    caso_uso          VARCHAR(50)  NOT NULL,      -- 'SOAP_EXTRACT' | 'S3_BACKUP'
    reintentos        INT          DEFAULT 0,
    fecha_creacion    TIMESTAMP    DEFAULT NOW()
    -- Eliminados: activo (redund. con estado), propietario (redund. con id_producto),
    --             ruta (reemplazada por clave_documento), fecha_actualizacion (derivable del histórico)
);

CREATE INDEX idx_documentos_estado_caso ON documentos(estado, caso_uso);
CREATE INDEX idx_documentos_id_doc     ON documentos(id_documento);


-- ==========================================
-- 2. TABLA DE AUDITORÍA: HISTÓRICO DE DOCUMENTOS
-- ==========================================
-- Cada intento de procesamiento genera un registro. Solo trazabilidad, sin binarios.

CREATE SEQUENCE seq_historico_documentos START WITH 1 INCREMENT BY 1;

CREATE TABLE historico_documentos (
    id              BIGINT       DEFAULT nextval('seq_historico_documentos') PRIMARY KEY,
    documento_id    BIGINT       NOT NULL,        -- FK a documentos.id
    nombre_archivo  VARCHAR(255),                 -- Vacío si no hubo descompresión ZIP
    operacion       VARCHAR(100),                 -- SEND_SOAP | UPLOAD_S3 | DECOMPRESS
    resultado       VARCHAR(50),                  -- SUCCESS | FAILURE
    codigo_error    VARCHAR(100),                 -- ProcessingResultCodes (granular)
    mensaje_error   VARCHAR(1000),
    stack_trace     TEXT,                         -- Para auditorías gubernamentales
    reintentos      INT          DEFAULT 0,
    fecha_inicio    TIMESTAMP,                    -- Inicio del intento
    fecha_fin       TIMESTAMP                     -- Fin del intento (también sirve de fecha_creacion)
    -- Eliminados: message_id (sin broker externo definido), fecha_creacion (redundante con fecha_inicio)
);

CREATE INDEX idx_historico_doc ON historico_documentos(documento_id);
```

### D. Definición de Entidades R2DBC (`*Entity.java`)

> **Corrección de Auditoría:** Las entidades reales son `DocumentEntity` y `DocumentHistoryEntity`. Se muestran tal como existen en el código actual, con sus anotaciones `@Column` en español.

```java
// 1. DocumentEntity.java — tabla 'documentos' (LIMPIO, SIN REDUNDANCIAS)
@Table("documentos")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class DocumentEntity {
    @Id @Column("id")                   private Long id;
    @Column("id_documento")             private String documentId;
    @Column("id_producto")              private String productId;
    @Column("clave_documento")          private String docKey;
    @Column("nombre")                   private String name;
    @Column("estado")                   private String state;
    @Column("version_contrato")         private String versionContract;
    @Column("mensaje_error")            private String errorMessage;
    @Column("es_zip")                   private Boolean isZip;
    @Column("nombre_zip_padre")         private String parentZipName;
    @Column("caso_uso")                 private String useCase;
    @Column("reintentos")               private Integer retryCount;
    @Column("fecha_creacion")           private LocalDateTime createdAt;
    // Eliminados: activo, propietario, ruta, fecha_actualizacion
}
```

```java
// 2. DocumentHistoryEntity.java — tabla 'historico_documentos' (LIMPIO)
@Table("historico_documentos")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class DocumentHistoryEntity {
    @Id @Column("id")                   private Long id;
    @Column("documento_id")             private Long documentId;
    @Column("nombre_archivo")           private String filename;
    @Column("operacion")                private String operation;
    @Column("resultado")                private String result;
    @Column("codigo_error")             private String errorCode;
    @Column("mensaje_error")            private String errorMessage;
    @Column("stack_trace")              private String stackTrace;
    @Column("reintentos")               private Integer retry;
    @Column("fecha_inicio")             private LocalDateTime startedAt;
    @Column("fecha_fin")                private LocalDateTime completedAt;
    // Eliminados: message_id (sin broker externo), fecha_creacion (redundante con fecha_inicio)
}
```

```java
// 3. DocumentHistory.java — Dominio (record inmutable para tránsito)
@Builder
public record DocumentHistory(
    Long id, Long documentId, String filename,
    String operation,
    String result, String errorCode, String errorMessage,
    String stackTrace, Integer retry,
    LocalDateTime startedAt, LocalDateTime completedAt
) {}
// Record válido: objeto inmutable de trazabilidad, NO mapeado por reflexión.
```

---

## 2. Refactorización de la Capa de Dominio (Clean Architecture)

### 2.1. Transformación a POJOs con Lombok
Actualmente se usan `record` (ej. `DownloadDTO`). Para que la arquitectura genérica de persistencia (que utiliza `ObjectMapper` / `ModelMapper`) funcione mediante Reflexión, debemos migrar todo a POJOs puros.

**Visualización del Cambio en el Código:**

```java
// [CÓDIGO ANTES] - Uso de Record incompatible con mappers por reflexión
public record DownloadDTO (
    String id,
    String title,
    String status,
    String parentId,
    String contentType,
    String extension,
    String fileContent
) {}
```

```java
// [CÓDIGO DESPUÉS] - POJO compatible con MapStruct/ObjectMapper
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadDTO {
    private String id;
    private String title;
    private String status;
    private String parentId;
    private String contentType;
    private String extension;
    private String fileContent; // Como llega del API externo (Base64)
}
```

### 2.2. Método de Conversión (String Base64 -> byte[])
**Actualización:** Dado que los archivos son livianos y el peso se valida previamente, la transformación sincrónica es segura y no representa un riesgo de OOM.
```java
public static ProductDataDocumentFile fromDto(DownloadDTO dto) {
    return ProductDataDocumentFile.builder()
        .id(dto.getId())
        .title(dto.getTitle())
        .parentId(dto.getParentId())
        .contentType(dto.getContentType())
        .extension(dto.getExtension())
        .fileContent(Base64.getDecoder().decode(dto.getFileContent())) // Conversión síncrona segura
        .build();
}
```

---

## 3. Estandarización de Infraestructura (Capa de Persistencia R2DBC)

### 3.1. Clase Base Genérica
Se utilizará `AbstractReactiveAdapterOperation` para inyectar automáticamente la lógica de conversión Dominio <-> DB.

### 3.2. Adaptador de Metadatos

**Visualización del Cambio en el Código:**

```java
// [CÓDIGO ANTES] - Adaptador Manual (Propenso a errores y repetitivo)
@Repository
public class ProductDataDocumentAdapter implements DocumentPersistenceGateway {
    private final ProductDataDocumentRepository repository;
    
    public Mono<ProductDataDocument> save(ProductDataDocument doc) {
        ProductDataDocumentEntity entity = new ProductDataDocumentEntity();
        entity.setId(doc.getId());
        entity.setTitle(doc.getTitle());
        // ... (10 líneas más de mapeo manual) ...
        return repository.save(entity)
            .map(saved -> {
                ProductDataDocument out = new ProductDataDocument();
                out.setId(saved.getId());
                // ... (10 líneas más de mapeo manual) ...
                return out;
            });
    }
}
```

```java
// [CÓDIGO DESPUÉS] - Adaptador Genérico Automatizado
@Repository
public class ProductDataDocumentAdapter 
    extends AbstractReactiveAdapterOperation<ProductDataDocument, ProductDataDocumentEntity, String, ProductDataDocumentRepository> 
    implements DocumentPersistenceGateway {

    public ProductDataDocumentAdapter(ProductDataDocumentRepository repository, ObjectMapper mapper) {
        // La clase base hereda save(), findById(), etc. y mapea automáticamente.
        super(repository, mapper, d -> mapper.map(d, ProductDataDocument.class), ProductDataDocumentEntity.class);
    }
}
```

### 3.3. Configuración del Mapper (`MapperConfig.java`)
Garantizar que existe el Bean global para los adaptadores.
```java
@Configuration
public class MapperConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapperImp();
    }
}
```

---

## 4. Casos de Uso y Arquitectura Limpia

### 4.1. Definición en `UseCasesConfig.java`
```java
@Configuration
@EnableConfigurationProperties(ProcessorsProperties.class)
public class UseCasesConfig {

    @Bean
    public ContractExtractUseCase contractExtractUseCase(
            PersistenceGateway persistence,
            RestGateway restGateway,
            ProcessorsProperties properties) { 
        
        // Aislamiento del dominio: El validador se instancia internamente.
        RulesBussinesGateway validator = new RulesBussinesService(properties.getTuConfig());
        return new ContractExtractUseCase(persistence, restGateway, validator);
    }
}
```

---

## 5. Arquitectura Simplificada (Ajustes de Contexto)

De acuerdo a las restricciones actuales del proyecto, se ha simplificado la arquitectura evitando la sobreingeniería:

### 5.1. Migración de Base de Datos (Fase Local)
Como el proyecto está en desarrollo local, **no es necesaria una migración Zero-Downtime**. Puedes simplemente hacer un `DROP TABLE` y regenerar las tablas con la nueva estructura de columnas limpias (ej. `filename` en lugar de `name`, eliminando `active` y `path`).

### 5.2. Persistencia Exclusiva de Metadatos
Dado que **solo se guarda la metadata**, la tabla de binarios (`ProductDataDocumentFile`) ya no requiere persistencia. El binario vivirá en memoria únicamente el tiempo necesario para transitar desde la descarga hasta el envío (vía HTTP/SOAP), simplificando enormemente el flujo transaccional.

### 5.3. Validaciones y Seguridad
Al ser una **fuente 100% confiable** y con archivos ligeros controlados, se descartan verificaciones de *Magic Numbers* o flujos reactivos complejos para descompresión/mapeo. El validador (`RulesBussinesService`) puede limitarse a las validaciones de negocio básicas configuradas en las propiedades.

## 6. Flujos de Negocio Core (Workflows)

La arquitectura debe soportar explícitamente los dos flujos principales del sistema, orquestados por sus respectivos casos de uso:

### 6.1. Flujo de Sincronización (`API_V1_PRODUCTS_SYNC`)
**Objetivo:** Descubrimiento y registro temprano.
1.  **Descarga:** Obtiene la metadata de los documentos asociados a un ID de producto.
2.  **Clasificación:** Valida la extensión para determinar si el archivo es un archivo comprimido (marca `isZip = true` o `false`).
3.  **Persistencia:** Guarda la metadata en la base de datos (tabla `ProductDataDocument`) con un estado inicial (ej. `PENDING`) para su encolamiento.
*Nota:* En esta fase **no** se descarga el contenido binario del archivo.

### 6.2. Flujo de Procesamiento y Envío (`API_V1_PRODUCTS`)
**Objetivo:** Procesamiento pesado, validación y enrutamiento.
1.  **Lectura:** Consulta los documentos en estado `PENDING` desde la base de datos.
2.  **Filtro Pre-Descarga (Optimización):** 
    *   Se evalúa la extensión conocida en la metadata. Si la extensión no es soportada por las reglas de negocio, se ignora/descarta.
    *   *Excepción:* Si el archivo está marcado como comprimido (`isZip`), **siempre** se descarga, ya que no se conocen las extensiones de su contenido interno hasta abrirlo.
3.  **Descarga Completa:** Si pasa el filtro, se consume el endpoint anterior para descargar la data binaria (`DownloadDTO`).
4.  **Validación Post-Descarga:** Se aplican las reglas de negocio (`RulesBussinesService`) sobre el archivo descargado o descomprimido (validación estricta de tamaño en bytes y extensión final).
5.  **Homologación (Condicional):** Si el destino es el API SOAP, se ejecuta la regla de homologación para ajustar la metadata al contrato SOAP esperado.
6.  **Enrutamiento:** Se envía el documento procesado al destino final (SOAP o S3).

## 7. Segregación por Caso de Uso (Prevención de Cruce de Datos)

Dado que un mismo producto puede tener documentos con destinos diferentes (SOAP o S3), las tablas `documentos` e `historico_documentos` incluyen un campo crítico: `caso_de_uso` (o `useCase`).

### 7.1. Integración en el Dominio
La entidad `ProductDataDocument` debe mapear explícitamente este campo:
```java
public class ProductDataDocument {
    // ... otros campos
    private String useCase; // Ej: "SOAP_EXTRACT", "S3_BACKUP"
}
```

### 7.2. Aislamiento en las Consultas (R2DBC)
Para garantizar que el proceso SOAP no consuma accidentalmente los documentos pendientes del proceso S3, los repositorios y el Gateway de Auditoría **deben** exigir este parámetro en cada lectura y escritura.

```java
// Ejemplo en el Repositorio R2DBC
@Query("SELECT * FROM documentos WHERE estado = 'PENDING' AND caso_uso = :useCase")
Flux<DocumentEntity> findPendingByUseCase(String useCase);
```

### 7.3. Contrato en la Clase Base
Cada caso de uso concreto (ej. `SoapDocumentUseCase`) debe declarar su identificador único al heredar de la clase base, inyectándolo en todas las operaciones de persistencia.

```java
public abstract class AbstractDocumentProcessingUseCase {
    // Cada hijo define su nombre de caso de uso (alineado al código real)
    protected abstract String implementationName();
    
    public Flux<FileUploadResponse> executePendingDocuments() {
        return persistencePort.findPendingDocumentsToday(implementationName(), startOfDay)
            .concatMap(this::processWithTracking)
            .doOnTerminate(() -> LOGGER.log(Level.INFO, "[{0}] Daily pipeline completed", implementationName()));
    }
}
```
De esta manera, la trazabilidad gubernamental reflejará exactamente qué ruta tomó cada documento, aislando completamente las reglas de negocio de SOAP respecto a las de S3.

## 8. Arquitectura de Trazabilidad y Auditoría Gubernamental

Para cumplir con regulaciones estrictas, el sistema utiliza un **modelo bifurcado** (patrón ya implementado):
- **`ProductState`**: controla el estado de ciclo de vida del documento en la tabla `documentos`.
- **`ProcessingResultCodes`** (ampliado): código granular del resultado de cada intento, guardado en `historico_documentos.codigo_error`.

Esto es arquitectónicamente superior a un mega-enum porque permite filtrar por estado sin mezclar semánticas.

### 8.1. Estados de Ciclo de Vida (`ProductState`)

```java
// ProductState.java — ESTADO ACTUAL (ya implementado)
public final class ProductState {
    public static final String PENDING      = "PENDING";       // En cola
    public static final String IN_PROGRESS  = "IN_PROGRESS";   // Siendo procesado (lock)
    public static final String PROCESSED    = "PROCESSED";     // Enviado exitosamente
    public static final String FAILED       = "FAILED";        // Fallo definitivo
    public static final String ERR_DUPLICATED_DOC = "ERR_DUPLICATED_DOC"; // Documento duplicado
    private ProductState() {}
}
```

### 8.2. Códigos de Error Granulares (`ProcessingResultCodes` — expandido)

```java
// ProcessingResultCodes.java — EXPANDIR con nuevos escenarios de red/API
public enum ProcessingResultCodes {
    // Ya existentes
    INVALID_BASE64,         // Contenido Base64 mal formado
    EMPTY_CONTENT,          // Archivo de 0 bytes
    INVALID_RESPONSE,       // Respuesta inesperada del API origen
    DECOMPRESSION_ERROR,    // ZIP corrupto o protegido con contraseña
    SIZE_EXCEEDED,          // Archivo supera el peso máximo
    PATTERN_MISMATCH,       // Extensión/MIME no soportada
    UNKNOWN_ERROR,          // Error crítico no catalogado
    BAD_GATEWAY,            // Origen respondió con error 5xx
    GATEWAY_TIMEOUT,        // Timeout en Origen o Destino
    SERVICE_UNAVAILABLE,    // Servicio caido (502/503)

    // NUEVOS — a agregar en la refactorización
    SOURCE_NOT_FOUND,       // Documento no existe en Origen (404)
    SOURCE_UNAUTHORIZED,    // Credenciales rechazadas en Origen (401/403)
    SOURCE_RATE_LIMIT,      // Límite de peticiones excedido en Origen (429)
    DEST_BAD_REQUEST,       // Destino rechazó el formato (400)
    DEST_UNAUTHORIZED,      // Fallo de autenticación en Destino
    DEST_QUOTA_EXCEEDED,    // Cuota de almacenamiento superada en Destino
    HOMOLOGATION_FAILED,    // Error al mapear metadata para el contrato SOAP
    MAX_RETRIES_EXCEEDED;   // Se agotaron los 3 reintentos permitidos
}
```

### 8.3. Flujo de Decisión: Estado + Código de Error

```java
// En AbstractDocumentProcessingUseCase.finalizeProcessing()
if (response.isSuccess()) {
    finalState = ProductState.PROCESSED;           // ← Estado final en documentos
    // historico: resultado='SUCCESS', codigo_error=null
} else if (isTransientError(response.getErrorCode()) && retryCount < 3) {
    finalState = ProductState.PENDING;             // Vuelve a cola para reintento
    // historico: resultado='FAILURE', codigo_error='GATEWAY_TIMEOUT', reintentos=N
} else {
    finalState = ProductState.FAILED;              // Fallo definitivo
    // historico: resultado='FAILURE', codigo_error='MAX_RETRIES_EXCEEDED'
}
```

```java
public interface AuditPersistenceGateway {
    // Guarda el resultado y suma +1 al retryCount si aplica
    Mono<Void> recordAuditTrail(ProductDataDocument document, DocumentAuditStatus status, String detailedMessage);
    
    // Consulta los PENDING aislados por caso de uso
    Flux<ProductDataDocument> findPendingForProcessing(String useCase);
}
```

### 8.3. Orquestador de Caso de Uso con Auditoría

Este flujo garantiza que tanto el éxito como cualquier fallo (comunicación, reglas o descompresión) sean interceptados y auditados formalmente.

```java
public Mono<Void> processSingleDocument(ProductDataDocument doc) {
    
    // 1. Filtro PRE-DESCARGA
    if (!doc.isZip() && !rulesGateway.isValidPreDownload(doc.getExtension())) {
        return auditPersistence.recordAuditTrail(doc, DocumentAuditStatus.SKIPPED_UNSUPPORTED_EXTENSION, "Extensión rechazada");
    }

    // 2. Descarga y Procesamiento
    return sourceSystem.downloadFile(doc.getId())
        .flatMap(file -> decompressIfNeeded(doc, file))
        .flatMap(file -> rulesGateway.validatePostDownload(file))
        .flatMap(destinationApi::sendDocument)
        
        // 3. Registro de Éxito
        .flatMap(success -> auditPersistence.recordAuditTrail(doc, DocumentAuditStatus.SUCCESS, "TraceID: " + success))
        
        // 4. Captura Global de Errores y Registro de Auditoría
        .onErrorResume(AuditTrackableException.class, e -> 
            auditPersistence.recordAuditTrail(doc, e.getAuditStatus(), e.getDetailedMessage())
        );
}
```

---

## 9. Estrategia de Observabilidad y Logs (Logging)

Para un sistema reactivo y desatendido, el manejo de logs es crítico para el diagnóstico en producción. Se debe implementar un estándar riguroso usando `java.util.logging.Logger` (u otro framework configurado como SLF4J/Log4j2) con un formato estructurado.

### 9.1. Reglas de Niveles de Log
*   **`Level.INFO` (Informativo):** Para hitos importantes del flujo de negocio.
    *   Ejemplo: *"Inicio de pipeline diario"*, *"Documento XYZ procesado exitosamente"*, *"Descarga completada en X milisegundos"*.
*   **`Level.WARNING` (Advertencia):** Para anomalías que no detienen el sistema, como reglas de negocio que descartan archivos o reintentos técnicos.
    *   Ejemplo: *"Documento XYZ descartado pre-descarga por extensión (.exe) no soportada"*, *"Timeout en Origen para Doc XYZ, intento 1/3"*.
*   **`Level.SEVERE` / `ERROR` (Crítico):** Para fallos definitivos, agotamiento de reintentos, o errores graves de memoria.
    *   Ejemplo: *"Fallo definitivo Doc XYZ: Archivo ZIP corrupto"*, *"Fallo fatal: Se superaron intentos máximos (3/3)"*.

### 9.2. Ejemplo de Implementación en el Orquestador

Todo log debe incluir el contexto de ejecución (como el ID del documento y el nombre del Caso de Uso).

```java
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractDocumentProcessingUseCase {
    
    protected final Logger LOGGER = Logger.getLogger(getClass().getName());

    public Flux<Void> execute() {
        return persistencePort.findPendingForProcessing(getUseCaseIdentifier())
            .doOnSubscribe(s -> LOGGER.log(Level.INFO, "[{0}] Pipeline started for pending documents", getUseCaseIdentifier()))
            .concatMap(this::processSingleDocument)
            .doOnTerminate(() -> LOGGER.log(Level.INFO, "[{0}] Pipeline execution completed", getUseCaseIdentifier()));
    }
    
    private Mono<Void> processSingleDocument(ProductDataDocument doc) {
        LOGGER.log(Level.INFO, "[{0}] Started processing document {1} for product {2}", 
            new Object[]{getUseCaseIdentifier(), doc.getIdDocumento(), doc.getIdProducto()});
            
        // ... Lógica Reactiva ...
        
        return sourceSystem.downloadFile(doc.getIdDocumento())
            .doOnError(e -> LOGGER.log(Level.WARNING, "[{0}] Technical error downloading doc {1}: {2}", 
                new Object[]{getUseCaseIdentifier(), doc.getIdDocumento(), e.getMessage()}));
    }
}
```

---

## 10. Estrategia de Actualización de Pruebas Unitarias

La refactorización masiva del modelo de datos y la introducción de los nuevos flujos obligan a actualizar la suite de pruebas unitarias para mantener una alta cobertura y prevenir regresiones.

### 10.1. Pruebas de Dominio (Mocks y POJOs)
*   **Ajuste de Accessors:** Las pruebas deben actualizarse para utilizar getters/setters estándar (ej. `doc.getId()`) en lugar de sintaxis de Records (`doc.id()`).
*   **Validación de Flujos:** Mockear las `ProcessorsProperties` para verificar que:
    *   Un archivo no zip y con extensión inválida es **descartado antes de llamar al Gateway de descarga** (ahorro de ancho de banda).
    *   Un archivo zip siempre llama al Gateway de descarga independientemente de su extensión inicial.
*   **Trazabilidad:** Usar `ArgumentCaptor` o `verify(auditPersistence).recordAuditTrail(...)` en Mockito para asegurar que todo error o éxito genere el registro correcto de `DocumentAuditStatus` y `detailedMessage`.

### 10.2. Pruebas de Infraestructura (Adaptadores)
*   Verificar que `ProductDataDocumentAdapter` delega correctamente la instanciación al `ObjectMapper`.
*   Asegurar que los Unit Tests del adaptador R2DBC no evalúen código comentado, sino la nueva herencia de `AbstractReactiveAdapterOperation`.

---

## 11. Unificación de Clases de Constantes

### 11.1. Problema Actual: Constantes Dispersas y Duplicadas

Al auditar el código se identificaron **7 clases/enums de constantes** esparcidos en diferentes capas, con duplicaciones graves:

| Clase Actual | Paquete | Responsabilidad |
|---|---|---|
| `ProductState` | `domain.entity` | Estados del ciclo de vida (`PENDING`, `FAILED`…) |
| `DocumentStatus` | `domain.entity` | Solo `SUCCESS` / `FAILURE` (redundante con `ProcessingResultCodes`) |
| `ProcessingResultCodes` | `domain.usecase` | Códigos de error de dominio |
| `SoapErrorCodes` | `infrastructure…soap` | Códigos de error SOAP (duplica `GATEWAY_TIMEOUT`, `BAD_GATEWAY`…) |
| `S3ErrorCodes` | `infrastructure…aws` | Códigos de error S3 (duplica los mismos 4 códigos) |
| `RestApiPaths` | `infrastructure…rest` | Rutas de los endpoints REST |
| `ApiConstants` | `infrastructure…rest` | Headers HTTP y tipos de procesador |
| `SoapConstants` | `infrastructure…soap` | Namespaces y elementos XML del envelope SOAP |

**Problemas concretos:**
- `GATEWAY_TIMEOUT`, `BAD_GATEWAY`, `SERVICE_UNAVAILABLE`, `UNKNOWN_ERROR` están definidos como `String` en `SoapErrorCodes` **y** `S3ErrorCodes`, y **también** como valores del enum `ProcessingResultCodes`.
- `DocumentStatus.SUCCESS/FAILURE` es redundante — se puede sustituir por `ProductState.PROCESSED/FAILED`.
- Las constantes de infraestructura (SOAP, S3, REST) deben **permanecer en su capa** pero los códigos de error de negocio deben vivir únicamente en el dominio.

---

### 11.2. Estrategia de Unificación

**Regla:** Un código de error se define **una sola vez** en el dominio (`ProcessingResultCodes`). La infraestructura referencia el enum, no define sus propias cadenas.

```java
// [ANTES] - Código duplicado en 3 lugares
// SoapErrorCodes.java
public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
// S3ErrorCodes.java
public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
// ProcessingResultCodes.java
GATEWAY_TIMEOUT,
```

```java
// [DESPUÉS] - Una sola fuente de verdad
// ProcessingResultCodes.java (dominio - AMPLIADO)
public enum ProcessingResultCodes {
    // Estados de resultado
    SUCCESS,
    FAILURE,
    // Errores de negocio
    INVALID_BASE64, EMPTY_CONTENT, INVALID_RESPONSE,
    DECOMPRESSION_ERROR, SIZE_EXCEEDED, PATTERN_MISMATCH,
    HOMOLOGATION_FAILED,
    // Errores de red (reintentables)
    BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE,
    // Errores de origen
    SOURCE_NOT_FOUND, SOURCE_UNAUTHORIZED, SOURCE_RATE_LIMIT,
    // Errores de destino
    DEST_BAD_REQUEST, DEST_UNAUTHORIZED, DEST_QUOTA_EXCEEDED,
    // Error final
    MAX_RETRIES_EXCEEDED, UNKNOWN_ERROR;
}

// SoapGatewayAdapter.java - usa el enum del dominio
return ProcessingResultCodes.GATEWAY_TIMEOUT.name();

// S3GatewayAdapter.java - usa el mismo enum
return ProcessingResultCodes.GATEWAY_TIMEOUT.name();
```

---

### 11.3. Clases a Eliminar

| Clase | Acción | Reemplazada por |
|---|---|---|
| `SoapErrorCodes` | **ELIMINAR** | `ProcessingResultCodes` (dominio) |
| `S3ErrorCodes` | **ELIMINAR** | `ProcessingResultCodes` (dominio) |
| `DocumentStatus` | **ELIMINAR** | `ProcessingResultCodes.SUCCESS/FAILURE` |

### 11.4. Clases a Mantener (cada una en su capa)

| Clase | Capa | Justificación |
|---|---|---|
| `ProductState` | Dominio | Estados de ciclo de vida del documento en BD |
| `ProcessingResultCodes` | Dominio | Única fuente de códigos de error y resultado |
| `RestApiPaths` | Infraestructura | Rutas REST — pertenecen a la capa de entrada |
| `ApiConstants` | Infraestructura | Headers HTTP — pertenecen a la capa de entrada |
| `SoapConstants` | Infraestructura | Namespaces XML — específicos del protocolo SOAP |

---

## Resumen de Tareas Pendientes:
1.  **DB:** Ejecutar el DDL actualizado recreando tablas `documentos` e `historico_documentos`.
2.  **Dominio:** Refactorizar `Document.java` eliminando `active`, `owner`, `path`, `updatedAt`.
3.  **Constantes:** Eliminar `SoapErrorCodes`, `S3ErrorCodes` y `DocumentStatus`; referenciar `ProcessingResultCodes` en los adaptadores SOAP y S3.
4.  **Infra:** Actualizar `DocumentEntity` y `DocumentHistoryEntity` con la estructura limpia (sin campos eliminados).
5.  **Dominio:** Expandir `ProcessingResultCodes` con los nuevos códigos de red (`SOURCE_NOT_FOUND`, `DEST_UNAUTHORIZED`, etc.).
6.  **Lógica:** Corregir `UseCasesConfig` inyectando `ProcessorsProperties` en lugar de `@Qualifier`.
