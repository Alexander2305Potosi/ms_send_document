# Plan: Tabla de Trazabilidad de Envios - `historico_documentos`

## Contexto

Necesitamos una tabla en H2 para registrar la trazabilidad completa de cada intento de envio de documentos a las APIs externas (SOAP/S3). Actualmente no hay persistencia del resultado del procesamiento; solo se devuelve como stream NDJSON.

La tabla permitira:
- Consultar historico de envios por producto
- Conocer el estado final de cada documento
- Saber razon de fallo, intentos y timestamps
- Identificar patrones de fallo (timeouts, rate limits, etc.)

---

## Estructura de la Tabla

### `historico_documentos`

| Campo | Tipo | Constraints | Descripcion |
|-------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Identificador unico |
| `nombre_producto` | VARCHAR | FK, NOT NULL | Referencia a `pending_products.product_id` |
| `nombre_documento` | VARCHAR | NOT NULL | ID del documento (original si vino de ZIP) |
| `nombre_archivo` | VARCHAR | NOT NULL | Nombre del archivo enviado |
| `nombre_comprimido` | VARCHAR | NULLABLE | Si `isZip=true`, nombre del archivo ZIP original |
| `estado` | VARCHAR | NOT NULL | SUCCESS / FAILURE / PENDING |
| `codigo_error` | VARCHAR | NULLABLE | INVALID_BASE64, TIMEOUT, BAD_GATEWAY, etc. |
| `razon_fallo` | VARCHAR | NULLABLE | Mensaje de error legible |
| `numero_intentos` | INT | NOT NULL, DEFAULT 1 | Numero de intentos |
| `fecha_envio` | TIMESTAMP | NULLABLE | Timestamp de envio exitoso |
| `fecha_fallo` | TIMESTAMP | NULLABLE | Timestamp de fallo |
| `fecha_creacion` | TIMESTAMP | NOT NULL | Fecha de creacion del registro |

### Estados posibles

| Estado | Significado |
|--------|-------------|
| `PENDING` | Documento en cola, aun no procesado |
| `SUCCESS` | Envio exitoso |
| `FAILURE` | Envio fallido (todos los intentos agotados) |
| `RETRY` | Reintentando actualmente |

---

## Entidad JPA

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/jpa/entity/DocumentTraceabilityEntity.java`

```java
@Entity
@Table(name = "historico_documentos")
public class DocumentTraceabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_producto", nullable = false)
    private String productId;

    @Column(name = "nombre_documento", nullable = false)
    private String documentId;

    @Column(name = "nombre_archivo", nullable = false)
    private String filename;

    @Column(name = "nombre_comprimido")
    private String compressedFilename;

    @Column(name = "estado", nullable = false)
    private String status;

    @Column(name = "codigo_error")
    private String errorCode;

    @Column(name = "razon_fallo")
    private String failureReason;

    @Column(name = "numero_intentos", nullable = false)
    private int attemptCount = 1;

    @Column(name = "fecha_envio")
    private LocalDateTime sentAt;

    @Column(name = "fecha_fallo")
    private LocalDateTime failedAt;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;
}
```

---

## Gateway de Persistencia

**Archivo:** `src/main/java/com/example/fileprocessor/domain/port/out/DocumentTraceabilityGateway.java`

```java
public interface DocumentTraceabilityGateway {
    Mono<Void> save(DocumentTraceability record);
    Flux<DocumentTraceability> findByProductId(String productId);
    Flux<DocumentTraceability> findByStatus(String status);
}
```

### Record de dominio

**Archivo:** `src/main/java/com/example/fileprocessor/domain/entity/DocumentTraceability.java`

```java
public record DocumentTraceability(
    Long id,
    String productId,
    String documentId,
    String filename,
    String compressedFilename,
    String status,
    String errorCode,
    String failureReason,
    int attemptCount,
    LocalDateTime sentAt,
    LocalDateTime failedAt,
    LocalDateTime createdAt
) {}
```

---

## Adapter JPA

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/jpa/DocumentTraceabilityAdapter.java`

```java
@Component
public class DocumentTraceabilityAdapter implements DocumentTraceabilityGateway {

    private final DocumentTraceabilityRepository repository;

    @Override
    public Mono<Void> save(DocumentTraceability record) {
        return Mono.fromRunnable(() -> {
            DocumentTraceabilityEntity entity = mapToEntity(record);
            repository.save(entity);
        });
    }

    @Override
    public Flux<DocumentTraceability> findByProductId(String productId) {
        return Flux.fromIterable(repository.findByProductId(productId))
            .map(this::toRecord);
    }

    private DocumentTraceabilityEntity mapToEntity(DocumentTraceability r) {
        return new DocumentTraceabilityEntity(...);
    }
}
```

---

## Repository

**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/jpa/repository/DocumentTraceabilityRepository.java`

```java
@Repository
public interface DocumentTraceabilityRepository extends JpaRepository<DocumentTraceabilityEntity, Long> {
    List<DocumentTraceabilityEntity> findByProductId(String productId);
    List<DocumentTraceabilityEntity> findByStatus(String status);
}
```

---

## Flujo de Integracion

### Modificacion en `AbstractDocumentProcessingUseCase`

El pipeline actual:
```java
.flatMap(validated -> uploadDocument(validated, product.productId()))
```

Nuevo pipeline:
```java
.flatMap(validated -> uploadDocument(validated, product.productId())
    .flatMap(result -> saveTraceability(validated, product.productId(), result)
        .thenReturn(result)))
```

### Nuevo metodo helper

```java
private Mono<Void> saveTraceability(ProductDocument doc, String productId, FileUploadResult result) {
    DocumentTraceability record = buildTraceabilityRecord(doc, productId, result);
    return sendTraceabilityGateway.save(record);
}

private DocumentTraceability buildTraceabilityRecord(ProductDocument doc, String productId, FileUploadResult result) {
    boolean isSuccess = result.isSuccess();
    return new DocumentTraceability(
        null,
        productId,
        doc.documentId(),
        doc.filename(),
        doc.isZip() ? doc.filename() : null,
        isSuccess ? "SUCCESS" : "FAILURE",
        result.errorCode(),
        result.message(),
        1,
        isSuccess ? LocalDateTime.now() : null,
        !isSuccess ? LocalDateTime.now() : null,
        LocalDateTime.now()
    );
}
```

---

## Codigos de Error en Traceability

Los `codigo_error` en `ProcessingResultCodes`, `SoapErrorCodes` y `S3ErrorCodes` se persisten directamente. Algunos relevantes:

| codigo_error | Categoria | Fase |
|-----------|-----------|------|
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

---

## Archivos a Crear/Modificar

| Accion | Archivo |
|--------|---------|
| Crear | `src/main/java/com/example/fileprocessor/domain/entity/DocumentTraceability.java` |
| Crear | `src/main/java/com/example/fileprocessor/domain/port/out/DocumentTraceabilityGateway.java` |
| Crear | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/jpa/entity/DocumentTraceabilityEntity.java` |
| Crear | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/jpa/repository/DocumentTraceabilityRepository.java` |
| Crear | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/jpa/DocumentTraceabilityAdapter.java` |
| Modificar | `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java` |
| Modificar | `src/main/java/com/example/fileprocessor/application/service/config/DomainConfig.java` |
| Modificar | `docs/README.md` (actualizar diagrama y flujo) |

---

## Verificacion

1. `./gradlew compileJava` — compila sin errores
2. `./gradlew test` — todos los tests pasan
3. `./gradlew bootRun` — inicia sin errores de bean
4. Consultar tabla via H2 console:
   ```sql
   SELECT * FROM historico_documentos ORDER BY fecha_creacion DESC;
   ```

---

## Notas

- La entidad NO usa Lombok (seguimos el patron existente en `PendingProductEntity`)
- Los `@Column(name = "...")` usan nombres en espanol para la BD (nombre_producto, nombre_documento, etc.)
- Los campos Java permanecen en ingles (productId, documentId, etc.)
- El campo `compressedFilename` indica si el archivo vino de un ZIP, guardando el nombre original del archivo comprimido
- Cada documento (incluidos los descomprimidos del ZIP) genera su propio registro de trazabilidad
- El `attemptCount` esta preparado para futuras mejoras con reintentos automaticos (actualmente siempre es 1)
