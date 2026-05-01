# Plan de Auditoria Integral - file-processor-service v3.0

## Contexto

Auditoria completa del microservicio `file-processor-service` (Spring Boot 3.3.5, WebFlux, Java 21, Clean Architecture). El servicio procesa documentos desde una API REST de productos y los envia a SOAP o S3.

**Se descartan hallazgos de analisis previos que ya fueron corregidos:**
- Violacion del Dependency Rule en `ProcessingException` -> CORREGIDO (constante `HEADER_TRACE_ID` ahora esta en dominio)
- `retrieveDocument()` con builder de 13 campos -> CORREGIDO (simplificado)
- `SoapGatewayAdapter.sendSoap()` de ~80 lineas -> CORREGIDO (reducido a ~20 lineas)
- FQN en lugar de imports -> CORREGIDO en varios archivos
- `AbstractDocumentProcessingUseCase` sobrecargado -> CORREGIDO (simplificado significativamente)

---

## HALLAZGOS VIGENTES

### CRITICOS (7) - Requieren accion inmediata

---

#### C1. `DocumentValidator` (dominio) importa `ProcessorsProperties.ProcessorConfig` (infraestructura)

- **Archivo**: `domain/service/DocumentValidator.java:4`
- **Problema**: Viola la regla de dependencia de Clean Architecture. El dominio no puede depender de infraestructura. La clase `ProcessorConfig` es un record anidado dentro de `ProcessorsProperties` que vive en `infrastructure.config`.
- **Solucion**: Crear un value object `ValidationConfig` en dominio y mapear desde `ProcessorConfig` en `DomainConfig`.

**Codigo actual (dominio dependiendo de infraestructura):**
```java
// domain/service/DocumentValidator.java
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties.ProcessorConfig; // MAL

public class DocumentValidator {
    private final ProcessorConfig config;
    public DocumentValidator(ProcessorConfig config) { this.config = config; }
}
```

**Codigo solucion:**
```java
// domain/service/ValidationConfig.java (NUEVO archivo en dominio)
public record ValidationConfig(Long maxFileSizeBytes, String filenamePattern) {}

// domain/service/DocumentValidator.java (corregido)
import com.example.fileprocessor.domain.service.ValidationConfig; // BIEN - dominio solo depende de dominio

public class DocumentValidator {
    private final ValidationConfig config;
    public DocumentValidator(ValidationConfig config) { this.config = config; }
    // ... resto del codigo igual pero usando ValidationConfig
}

// application/app/service/config/DomainConfig.java (mapeo en capa de aplicacion)
@Bean
@ConditionalOnBean(SoapGateway.class)
public SoapDocumentProcessingUseCase soapDocumentUseCase(...) {
    // Mapeo de infraestructura -> dominio
    var soapConfig = properties.soap();
    var validationConfig = new ValidationConfig(soapConfig.maxFileSizeBytes(), soapConfig.filenamePattern());
    return new SoapDocumentProcessingUseCase(productRestGateway, soapGateway, new DocumentValidator(validationConfig));
}
```

---

#### C2. `ProcessingResultCodes` (dominio) contiene codigos HTTP/AWS

- **Archivo**: `domain/usecase/ProcessingResultCodes.java:19-27`
- **Problema**: `GATEWAY_TIMEOUT`, `BAD_GATEWAY`, `CLIENT_ERROR`, `ACCESS_DENIED_ERROR`, `NOT_FOUND_ERROR`, `SERVICE_UNAVAILABLE_ERROR` son conceptos de infraestructura (HTTP/AWS), no de dominio de negocio.
- **Solucion**: Separar en dos constantes: codigos de dominio (negocio) y codigos de infraestructura.

**Codigo actual:**
```java
// domain/usecase/ProcessingResultCodes.java - MEZCLA dominio e infraestructura
public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";      // HTTP -> infra
public static final String BAD_GATEWAY = "BAD_GATEWAY";              // HTTP -> infra
public static final String CLIENT_ERROR = "CLIENT_ERROR";            // HTTP -> infra
public static final String ACCESS_DENIED_ERROR = "ACCESS_DENIED_ERROR";  // AWS -> infra
public static final String NOT_FOUND_ERROR = "NOT_FOUND_ERROR";      // AWS -> infra
public static final String SERVICE_UNAVAILABLE_ERROR = "SERVICE_UNAVAILABLE_ERROR"; // AWS -> infra
```

**Codigo solucion:**
```java
// domain/usecase/ProcessingResultCodes.java (solo conceptos de negocio)
public final class ProcessingResultCodes {
    private ProcessingResultCodes() {}
    public static final String EMPTY_CONTENT = "EMPTY_CONTENT";
    public static final String INVALID_BASE64 = "INVALID_BASE64";
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String UPLOAD_FAILED = "UPLOAD_FAILED";  // concepto de negocio generico
}

// infrastructure/drivenadapters/aws/S3ErrorCodes.java (NUEVO archivo en infra)
public final class S3ErrorCodes {
    private S3ErrorCodes() {}
    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}
```

---

#### C4. `SoapGatewayAdapter.onErrorReturn` traga todas las excepciones

- **Archivo**: `infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:69`
- **Problema**: Captura `NullPointerException`, errores de programacion, etc. y los convierte silenciosamente en resultado fallido sin traceId ni mensaje. Oculta bugs reales.
- **Solucion**: Reemplazar `onErrorReturn` por `onErrorResume` que solo capture excepciones conocidas y relance las inesperadas.

**Codigo actual:**
```java
// SoapGatewayAdapter.java linea 69 - TRAGA TODO
.onErrorReturn(toFileUploadResultError());  // NPE, bugs, todo se vuelve fallo silencioso
```

**Codigo solucion:**
```java
// SoapGatewayAdapter.java - SOLO captura errores de red/HTTP conocidos
.onErrorResume(WebClientResponseException.class, ex -> {
    log.error("SOAP HTTP error for traceId={}: {} {}", traceId, ex.getStatusCode(), ex.getMessage());
    return Mono.just(buildErrorResult(traceId, ProcessingResultCodes.BAD_GATEWAY, ex.getMessage()));
})
.onErrorResume(TimeoutException.class, ex -> {
    log.error("SOAP timeout for traceId={}", traceId);
    return Mono.just(buildErrorResult(traceId, ProcessingResultCodes.GATEWAY_TIMEOUT, "Timeout after "
        + properties.timeoutSeconds() + "s"));
})
.onErrorResume(IOException.class, ex -> {
    log.error("SOAP IO error for traceId={}: {}", traceId, ex.getMessage());
    return Mono.just(buildErrorResult(traceId, ProcessingResultCodes.UNKNOWN_ERROR, ex.getMessage()));
})
// Errores de programacion (NPE, etc.) NO se capturan -> se propagan al handler global
.doOnError(e -> {
    if (!(e instanceof WebClientResponseException || e instanceof TimeoutException || e instanceof IOException)) {
        log.error("UNEXPECTED error in SOAP adapter - this is a bug", e);
    }
});

// Metodo helper
private FileUploadResult buildErrorResult(String traceId, String errorCode, String message) {
    return FileUploadResult.builder()
        .status(DocumentStatus.FAILURE.name())
        .errorCode(errorCode)
        .traceId(traceId)
        .message(message)
        .processedAt(Instant.now())
        .success(false)
        .build();
}
```

---

#### C5. Posible NPE en `S3GatewayAdapter` con contenido null

- **Archivo**: `infrastructure/drivenadapters/aws/S3GatewayAdapter.java:59`
- **Problema**: `request.getContent()` puede ser null y se pasa a `AsyncRequestBody.fromBytes()` que lanzara NPE si recibe null. Aunque hay un null check en la linea 51 para `contentLength`, `fromBytes` se llama en la linea 59 sin proteccion.
- **Solucion**: Agregar null check explicito y retornar error controlado.

**Codigo actual:**
```java
// S3GatewayAdapter.java lineas 51, 59
.contentLength(request.getContent() != null ? (long) request.getContent().length : 0L)
// ...
CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest,
    AsyncRequestBody.fromBytes(request.getContent()));  // NPE si content es null!
```

**Codigo solucion:**
```java
// S3GatewayAdapter.java - extraer y validar antes de usar
byte[] content = request.getContent();
if (content == null || content.length == 0) {
    log.warn("S3 upload skipped for documentId={} - content is null or empty", request.getDocumentId());
    return Mono.just(FileUploadResult.builder()
        .status(DocumentStatus.FAILURE.name())
        .errorCode(ProcessingResultCodes.EMPTY_CONTENT)
        .traceId(traceId)
        .message("Cannot upload empty content to S3")
        .processedAt(Instant.now())
        .success(false)
        .build());
}

PutObjectRequest putRequest = PutObjectRequest.builder()
    .bucket(s3Properties.bucketName())
    .key(key)
    .contentType(request.getContentType())
    .contentLength((long) content.length)
    .metadata(Map.of(...))
    .build();

CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest,
    AsyncRequestBody.fromBytes(content));  // content garantizado no-null
```

---

#### C6. Perfil S3 en `application.yml` usa prefijo incorrecto

- **Archivos**: `application.yml:84-91`, `S3Properties.java`
- **Problema**: El perfil `s3` en `application.yml` declara `aws.s3.*` pero `S3Properties` usa `@ConfigurationProperties(prefix = "app.aws.s3")`. Las propiedades bajo `aws.s3.*` nunca son leidas. Es codigo de configuracion muerto.
- **Solucion**: Corregir el prefijo en el perfil `s3` a `app.aws.s3.*`.

**Codigo actual (application.yml perfil s3):**
```yaml
# application.yml lineas 79-91 - PREFIJO INCORRECTO
---
spring:
  config:
    activate:
      on-profile: s3
  aws:          # MAL - deberia ser app.aws
    s3:
      bucket-name: documents-bucket
      region: us-east-1
      endpoint: http://localhost:4566
      path-style-access: true
      retry-attempts: 3
      retry-backoff-millis: 500
```

**Codigo solucion:**
```yaml
# application.yml - PREFIJO CORRECTO
---
spring:
  config:
    activate:
      on-profile: s3

app:                # BIEN - coincide con @ConfigurationProperties(prefix = "app.aws.s3")
  aws:
    s3:
      bucket-name: documents-bucket
      region: us-east-1
      endpoint: http://localhost:4566
      path-style-access: true
```

---

#### C7. `.collectList()` en `ProductHandler` puede causar OOM

- **Archivo**: `infrastructure/entrypoints/rest/handler/ProductHandler.java:44-48`
- **Problema**: Acumula TODOS los resultados en una lista en memoria antes de enviar respuesta HTTP. Con miles de documentos = riesgo de OutOfMemory. Ademas, el cliente no ve ningun resultado parcial hasta que todo termina.
- **Solucion**: Devolver `Flux<FileUploadResult>` como stream sin `collectList()`.

**Codigo actual:**
```java
// ProductHandler.java lineas 43-49
return Mono.deferContextual(ctx -> ServerResponse.accepted()
    .bodyValue(getProcessor(processorType).executePendingDocuments()
        .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
            result.getCorrelationId(), result.getStatus()))
        .doOnError(error -> log.error("Processing failed for traceId {}: {}", traceId, error.getMessage()))
        .collectList()));  // PELIGROSO: acumula todo en memoria
```

**Codigo solucion:**
```java
// ProductHandler.java - Streaming sin acumular en memoria
return Mono.deferContextual(ctx -> {
    Flux<FileUploadResult> results = getProcessor(processorType)
        .executePendingDocuments()
        .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
            result.getCorrelationId(), result.getStatus()))
        .doOnError(error -> log.error("Processing failed for traceId {}: {}", traceId, error.getMessage()));

    return ServerResponse.ok()  // 200 en vez de 202
        .contentType(MediaType.APPLICATION_NDJSON)  // Newline-delimited JSON para streaming
        .body(results, FileUploadResult.class);
}).contextWrite(ctx -> ctx.put(HEADER_TRACE_ID, traceId));
```

---

### ALTOS (12) - Abordar en este sprint

---

#### A1. `onErrorResume` duplicado entre `S3DocumentProcessingUseCase` y `SoapDocumentProcessingUseCase`

- **Archivos**: `domain/usecase/S3DocumentProcessingUseCase.java:34-43`, `SoapDocumentProcessingUseCase.java:31-40`
- **Problema**: Ambos metodos `uploadDocument()` tienen bloques identicos de manejo de errores (instanceof ProcessingException, extraer errorCode, construir FileUploadResult de fallo). Solo difiere el gateway llamado.
- **Solucion**: Extraer metodo protegido `handleUploadError()` en `AbstractDocumentProcessingUseCase`.

**Codigo actual (duplicado en ambas clases):**
```java
// SoapDocumentProcessingUseCase.java y S3DocumentProcessingUseCase.java
.onErrorResume(error -> {
    String errorCode = error instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    return Mono.just(FileUploadResult.builder()
        .status(DocumentStatus.FAILURE.name())
        .errorCode(errorCode)
        .processedAt(Instant.now())
        .success(false)
        .build());
});
```

**Codigo solucion:**
```java
// AbstractDocumentProcessingUseCase.java - metodo compartido
protected Mono<FileUploadResult> handleUploadError(Throwable error) {
    String errorCode = error instanceof ProcessingException pe
        ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    log.error("Upload failed with errorCode={}: {}", errorCode, error.getMessage());
    return Mono.just(FileUploadResult.builder()
        .status(DocumentStatus.FAILURE.name())
        .errorCode(errorCode)
        .processedAt(Instant.now())
        .success(false)
        .build());
}

// SoapDocumentProcessingUseCase.java - simplificado
@Override
protected Mono<FileUploadResult> uploadDocument(ProductDocument doc, String productId) {
    return soapGateway.send(buildFileUploadRequest(doc, null))
        .onErrorResume(this::handleUploadError);
}

// S3DocumentProcessingUseCase.java - simplificado
@Override
protected Mono<FileUploadResult> uploadDocument(ProductDocument doc, String productId) {
    return s3Gateway.send(buildFileUploadRequest(doc, doc.origin()))
        .onErrorResume(this::handleUploadError);
}
```

---

#### A2. `ProductStatus` enum completamente sin uso

- **Archivo**: `domain/entity/ProductStatus.java`
- **Problema**: 7 valores definidos (PENDING, PROCESSING, SUCCESS, PARTIAL_FAILURE, COMPLETED_WITH_SKIPS, COMPLETED_WITH_NOT_SENT, COMPLETED_WITH_FAILURES), cero referencias en todo el codigo de produccion.
- **Solucion**: Eliminar el archivo completo.

**Accion**: `rm src/main/java/com/example/fileprocessor/domain/entity/ProductStatus.java`

Y eliminar su test correspondiente:
**Accion**: `rm src/test/java/com/example/fileprocessor/domain/entity/ProductStatusTest.java`

---

#### A3. 6 valores de `DocumentStatus` nunca usados

- **Archivo**: `domain/entity/DocumentStatus.java`
- **Problema**: Solo `SUCCESS` y `FAILURE` se usan en codigo productivo. `PENDING`, `PROCESSING`, `RETRY`, `SKIPPED`, `NOT_SENT`, `DEAD_LETTER` son codigo muerto que confunde y sugiere funcionalidad que no existe.
- **Solucion**: Eliminar valores no usados. Si se planea usarlos en el futuro, documentarlo en un issue/README.

**Codigo actual:**
```java
public enum DocumentStatus {
    PENDING,       // nunca usado
    PROCESSING,    // nunca usado
    RETRY,         // nunca usado
    SUCCESS,       // usado
    FAILURE,       // usado
    SKIPPED,       // nunca usado
    NOT_SENT,      // nunca usado
    DEAD_LETTER;   // nunca usado
}
```

**Codigo solucion (opcion minimalista):**
```java
public enum DocumentStatus {
    SUCCESS,
    FAILURE;
}
```

---

#### A4. Constante `"message-id"` duplicada

- **Archivos**: `ApiConstants.java:18` y `ProcessingException.java:13`
- **Problema**: La misma cadena `"message-id"` esta definida en dominio e infraestructura. Cambiar en un lugar y olvidar el otro causa bugs.
- **Solucion**: Eliminar de `ApiConstants` y usar solo la de dominio via import static.

**Codigo actual:**
```java
// ApiConstants.java (infraestructura)
public static final String HEADER_TRACE_ID = "message-id";

// ProcessingException.java (dominio)
public static final String HEADER_TRACE_ID = "message-id";
```

**Codigo solucion:**
```java
// ApiConstants.java - DELEGA a dominio
import static com.example.fileprocessor.domain.exception.ProcessingException.HEADER_TRACE_ID;
// Eliminar la constante propia, usar HEADER_TRACE_ID directamente

// Nota: si ApiConstants necesita su propio nombre semantico, usar un alias:
// public static final String HTTP_HEADER_TRACE_ID = ProcessingException.HEADER_TRACE_ID;
```

---

#### A5. ELIMINAR - Resilience4j declarado pero sin implementar

- **Archivos**: `build.gradle.kts:70-71`, `application.yml`
- **Problema**: Las dependencias `resilience4j-circuitbreaker` y `resilience4j-reactor` (2.2.0) estan en el classpath pero no se usa `@CircuitBreaker`, ni `CircuitBreakerOperator`, ni `Mono.transformDeferred(CircuitBreakerOperator.of(...))` en ninguna parte.
- **Accion**: ELIMINAR todas las trazas de Resilience4j del proyecto (dependencias, configuraciones, documentación).

**Codigo a eliminar:**
```kotlin
// build.gradle.kts - eliminar estas lineas
// implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
// implementation("io.github.resilience4j:resilience4j-reactor:2.2.0")
```

---

#### A6. `application-prod.yml` incompleto

- **Archivo**: `application-prod.yml`
- **Problema**: Solo define logging, server shutdown/timeout, y SOAP endpoint. Faltan `document-rest`, `processors`, y `aws.s3`. En produccion estas propiedades deben venir de variables de entorno no documentadas.
- **Solucion**: Agregar todas las propiedades requeridas con placeholders de variables de entorno explicitos.

**Codigo actual:**
```yaml
logging:
  level:
    com.example.fileprocessor: WARN
server:
  shutdown: graceful
  timeout: 30s
app:
  soap:
    endpoint: ${SOAP_ENDPOINT:http://localhost:9000/soap/fileservice}
    timeout-seconds: 15
    retry-attempts: 2
```

**Codigo solucion:**
```yaml
logging:
  level:
    com.example.fileprocessor: WARN
    org.springframework.web.reactive: WARN

server:
  shutdown: graceful
  timeout: 30s

app:
  security:
    api-key: ${API_KEY}  # REQUERIDO en prod, sin default
  soap:
    endpoint: ${SOAP_ENDPOINT}
    timeout-seconds: 15
    retry-attempts: 2
  document-rest:
    endpoint: ${DOCUMENT_REST_ENDPOINT}
    products-path: /api/products
    product-documents-path: /api/products/{productId}/documents
    timeout-seconds: 10
  aws:
    s3:
      bucket-name: ${AWS_BUCKET}
      region: ${AWS_REGION}
      access-key: ${AWS_ACCESS_KEY}
      secret-key: ${AWS_SECRET_KEY}
      retry-attempts: 3
      retry-backoff-millis: 500
      timeout-seconds: 30
  processors:
    s3:
      max-file-size-bytes: 52428800
      filename-pattern: ".*\\.(pdf|csv)$"
    soap:
      max-file-size-bytes: 10485760
      filename-pattern: ".*\\.(pdf|docx|txt)$"
```

---

#### A7. Perfil `windows` huerfano

- **Archivo**: `application-windows.yml`
- **Problema**: Ningun script activa este perfil. `start-dev.bat` usa `--spring.profiles.active=dev`, no `windows`. El archivo es ruido.
- **Solucion**: Eliminar el archivo `application-windows.yml` ya que replica propiedades que ya estan en `application.yml`.

**Accion**: `rm src/main/resources/application-windows.yml`

---

#### A8. Dependencias de test no usadas (okhttp/mockwebserver)

- **Archivo**: `build.gradle.kts:85-86`
- **Problema**: `com.squareup.okhttp3:mockwebserver:4.12.0` y `com.squareup.okhttp3:okhttp:4.12.0` declarados como `testImplementation` pero ningun test en el proyecto los usa.
- **Solucion**: Eliminar las dependencias.

**Accion en build.gradle.kts:**
```kotlin
// ELIMINAR estas lineas:
// testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
// testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
```

---

#### A9. `reactor-tools` sin inicializacion

- **Archivo**: `build.gradle.kts:77`
- **Problema**: La dependencia `io.projectreactor:reactor-tools` requiere inicializacion explicita con `ReactorDebugAgent.init()` o `-Dreactor.tools.logging.enabled=true` en la JVM. Sin eso, es codigo completamente inerte.
- **Solucion**: Inicializar en `main()` o eliminar la dependencia.

**Opcion A - Inicializar (recomendado para dev/debug):**
```java
// Application.java
public static void main(String[] args) {
    ReactorDebugAgent.init();  // solo debug, no recomendado en produccion
    SpringApplication.run(Application.class, args);
}
```

**Opcion B - Eliminar (recomendado, solo es util para debugging local):**
```kotlin
// build.gradle.kts - eliminar
// implementation("io.projectreactor:reactor-tools")
```

---

#### A10. `S3Properties`: defaults en constructor nunca se ejecutan para valores invalidos

- **Archivo**: `infrastructure/drivenadapters/aws/config/S3Properties.java:39-43`
- **Problema**: Las anotaciones `@Min` en los campos (`retryAttempts`, `retryBackoffMillis`, `timeoutSeconds`) son validadas por Spring Boot ANTES del constructor compacto. Si un valor es menor que el minimo, `@Validated` lanza `BindException` y el codigo de correccion del constructor jamas se ejecuta. Los defaults en el constructor son codigo muerto.
- **Solucion**: Eliminar `@Min` y solo usar defaults en el constructor compacto, O eliminar defaults y solo usar `@Min` con documentacion.

**Codigo actual (conflicto @Min vs defaults):**
```java
@Min(0)                    // rechaza valores < 0 con BindException
int retryAttempts,         // el constructor NUNCA recibe valores < 0

// ...
public S3Properties {
    if (retryAttempts < 0) retryAttempts = 3;  // CODIGO MUERTO - nunca se ejecuta
}
```

**Codigo solucion:**
```java
// Opcion: eliminar @Min, usar solo defaults
public record S3Properties(
    @NotBlank String bucketName,
    @NotBlank String region,
    String endpoint,
    boolean pathStyleAccess,
    String accessKey,       // quitar @NotBlank si AwsConfig tiene fallback
    String secretKey,       // quitar @NotBlank si AwsConfig tiene fallback
    int retryAttempts,
    int retryBackoffMillis,
    int timeoutSeconds,
    String keyPrefix
) {
    public S3Properties {
        // defaults reales que SI se ejecutan
        if (retryAttempts <= 0) retryAttempts = 3;
        if (retryBackoffMillis < 100) retryBackoffMillis = 500;
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (keyPrefix == null || keyPrefix.isBlank()) keyPrefix = "documents/";
    }
}
```

Tambien hay conflicto en `accessKey`/`secretKey`: `S3Properties` las requiere `@NotBlank`, pero `AwsConfig` tiene fallback a `DefaultCredentialsProvider` cuando son blank. Si realmente hay fallback, quitar `@NotBlank` de esos campos.

---

#### A11. `parentFolder` y `childFolder` hardcodeados a `"."`

- **Archivo**: `domain/usecase/AbstractDocumentProcessingUseCase.java:48-49`
- **Problema**: Siempre se pasan como `"."`. Si siempre es asi, son campos innecesarios en `FileUploadRequest`. Si tienen significado, deberian ser configurables.
- **Solucion**: Eliminarlos del builder y de `FileUploadRequest` si no se usan. Si se planea usarlos, agregarlos a la configuracion.

**Codigo actual:**
```java
// AbstractDocumentProcessingUseCase.java
return FileUploadRequest.builder()
    // ...
    .parentFolder(".")   // siempre "."
    .childFolder(".")    // siempre "."
    .origin(origin)
    .build();
```

**Codigo solucion (eliminar del builder):**
```java
// AbstractDocumentProcessingUseCase.java - simplificado
return FileUploadRequest.builder()
    .documentId(doc.documentId())
    .content(doc.content() != null ? doc.content() : new byte[0])
    .filename(doc.filename())
    .contentType(doc.contentType())
    .fileSize(doc.size())
    .origin(origin)
    .build();
```

Luego eliminar los campos `parentFolder` y `childFolder` de `FileUploadRequest.java` y de `UploadFileRequest.java`, y limpiar `SoapMapper.toSoapXml()` que los usa en el constructor de `UploadFileRequest` (lineas 51-52).

---

#### A12. `ExternalServiceResponse.isSuccess()` acoplado a `DocumentStatus.SUCCESS.name()`

- **Archivo**: `domain/entity/ExternalServiceResponse.java:24`
- **Problema**: Usa `DocumentStatus.SUCCESS.name()` para interpretar la respuesta de un servicio SOAP externo. Si el protocolo externo cambia su formato de status, el chequeo se vuelve incorrecto. Ademas, `DocumentStatus` es un enum de estado interno, no un codigo de protocolo externo.
- **Solucion**: Ya existe `STATUS_OK = "OK"` en la clase. Usar solo ese o verificar explicitamente contra los valores conocidos del protocolo SOAP.

**Codigo actual:**
```java
public boolean isSuccess() {
    return DocumentStatus.SUCCESS.name().equalsIgnoreCase(status)
        || STATUS_OK.equalsIgnoreCase(status);
}
```

**Codigo solucion:**
```java
public boolean isSuccess() {
    return STATUS_OK.equalsIgnoreCase(status)
        || "SUCCESS".equalsIgnoreCase(status);
    // NOTA: "SUCCESS" aqui es una constante de protocolo SOAP, no de DocumentStatus
}
```

---

### MEDIOS (10) - Deuda tecnica

---

#### M1. `Base64Utils.decode()` nunca llamado

- **Archivo**: `domain/util/Base64Utils.java:15-19`
- **Problema**: El metodo `decode(String)` existe pero ninguna clase en produccion lo llama. Solo se usan `decodeSafe()` y `encode()`.
- **Solucion**: Eliminar el metodo.

**Accion**: Eliminar el metodo `decode()` de `Base64Utils.java`.

---

#### M2. `SoapMapper.fromSoapXml` captura y relanza `ProcessingException` inutilmente

- **Archivo**: `infrastructure/helpers/soap/mapper/SoapMapper.java:98-99`
- **Problema**: Hay un bloque `catch (ProcessingException e) { throw e; }` que captura solo para relanzar. Es completamente redundante.
- **Solucion**: Eliminar el bloque catch.

**Codigo actual:**
```java
try {
    UploadFileResponse response = envelopeWrapper.unwrapResponse(xml, UploadFileResponse.class);
    // ...
} catch (ProcessingException e) {
    throw e;  // REDUNDANTE - captura y relanza sin hacer nada
} catch (Exception e) {
    // ...
}
```

**Codigo solucion:**
```java
try {
    UploadFileResponse response = envelopeWrapper.unwrapResponse(xml, UploadFileResponse.class);
    // ...
} catch (Exception e) {
    if (e instanceof ProcessingException pe) throw pe;  // relanzar directo
    log.error("Error parsing SOAP response: {}", e.getMessage());
    throw ProcessingException.withTraceId(
        "Failed to parse SOAP response: " + e.getMessage(),
        ProcessingResultCodes.INVALID_RESPONSE, "", e);
}
```

---

#### M3. `SoapMapper.toFullSoapMessage` recibe `documentId` que no usa

- **Archivo**: `infrastructure/helpers/soap/mapper/SoapMapper.java:59`
- **Problema**: El metodo `toFullSoapMessage(FileUploadRequest fileUploadRequest)` usa el contenido del objeto `fileUploadRequest` pero este no contiene un campo `documentId` que sea usado; el metodo `toSoapXml()` que invoca internamente tampoco lo usa. No hay parametro `documentId` separado - el agent anterior lo reporto incorrectamente. Revisando el codigo actual, el metodo recibe `FileUploadRequest` que SI tiene `documentId` (getter) pero no se usa en el XML generado.
- **Solucion**: Si `documentId` no es requerido en el XML SOAP, no se necesita accion. Si deberia aparecer, agregarlo al `UploadFileRequest` JAXB y al metodo `toSoapXml()`.

**Accion**: Verificar con el equipo si el servicio SOAP espera `documentId` en el XML. Si no, el codigo actual es correcto.

---

#### M4. `SoapProperties`: `retryAttempts`, `retryBackoffMillis`, `maxErrorBodyLength` sin consumidor

- **Archivo**: `infrastructure/drivenadapters/soap/config/SoapProperties.java:18-25`
- **Problema**: Tras la simplificacion del `SoapGatewayAdapter`, estos campos ya no son leidos por ninguna clase. Solo `endpoint` y `timeoutSeconds` se usan.
- **Solucion**: Eliminar los campos huerfanos del record.

**Codigo actual:**
```java
@ConfigurationProperties(prefix = "app.soap")
public record SoapProperties(
    @NotBlank String endpoint,
    @Min(1) int timeoutSeconds,
    @Min(0) int retryAttempts,        // HUERFANO
    @Min(100) int retryBackoffMillis,  // HUERFANO
    @Min(100) int maxErrorBodyLength   // HUERFANO
) { ... }
```

**Codigo solucion:**
```java
@ConfigurationProperties(prefix = "app.soap")
public record SoapProperties(
    @NotBlank String endpoint,
    @Min(1) int timeoutSeconds
) {
    public SoapProperties {
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
    }
}
```

Y limpiar las propiedades correspondientes en `application.yml` y `application-prod.yml` (`retry-attempts`, `retry-backoff-millis`, `max-error-body-length`).

---

#### M5. `DomainConfig` retorna `null` desde metodos `@Bean`

- **Archivo**: `application/app/service/config/DomainConfig.java:29-31, 44-46`
- **Problema**: `@ConditionalOnBean` ya garantiza que el metodo no se ejecute si el bean no existe. Retornar `null` de un `@Bean` es mala practica y puede causar warnings en logs de Spring.
- **Solucion**: Eliminar los null checks redundantes.

**Codigo actual:**
```java
@Bean
@ConditionalOnBean(SoapGateway.class)
public SoapDocumentProcessingUseCase soapDocumentUseCase(
        ProductRestGateway productRestGateway,
        ObjectProvider<SoapGateway> soapGatewayProvider, ProcessorsProperties properties) {
    SoapGateway soapGateway = soapGatewayProvider.getIfAvailable();
    if (soapGateway == null) return null;   // REDUNDANTE por @ConditionalOnBean
    return new SoapDocumentProcessingUseCase(...);
}
```

**Codigo solucion:**
```java
@Bean
@ConditionalOnBean(SoapGateway.class)
public SoapDocumentProcessingUseCase soapDocumentUseCase(
        ProductRestGateway productRestGateway, SoapGateway soapGateway,
        ProcessorsProperties properties) {
    var soapConfig = properties.soap();
    return new SoapDocumentProcessingUseCase(
        productRestGateway, soapGateway,
        new DocumentValidator(new ValidationConfig(soapConfig.maxFileSizeBytes(), soapConfig.filenamePattern())));
}
```

---

#### M6. Paquete `application.app.service.config` con `app` duplicado

- **Archivo**: `DomainConfig.java` (paquete)
- **Problema**: El paquete `com.example.fileprocessor.application.app.service.config` contiene `app` duplicado (`application.app`), probablemente un artefacto de refactoring o generacion automatica.
- **Solucion**: Mover `DomainConfig.java` a `com.example.fileprocessor.application.service.config` (o mejor: `com.example.fileprocessor.infrastructure.config`).

**Accion**: Mover archivo de `application/app/service/config/DomainConfig.java` a `infrastructure/config/DomainConfig.java` y actualizar el `package` y los imports de quienes lo referencien.

---

#### M7. Configuracion R2DBC/H2 sin entidades ni repositorios

- **Archivo**: `application.yml:8-14`
- **Problema**: Hay config de `spring.r2dbc` y `spring.h2.console` y dependencias `r2dbc-h2` + `h2`, pero no existe ninguna entidad `@Table`, ni repositorio `ReactiveCrudRepository`, ni schema SQL, ni migraciones. Es peso muerto en classpath y config.
- **Solucion**: Eliminar la configuracion y dependencias de BD, o implementar la capa de persistencia.

**Opcion A - Eliminar:**
```yaml
# application.yml - eliminar estas lineas
# spring:
#   r2dbc:
#     url: r2dbc:h2:mem:///fileprocessor;DB_CLOSE_DELAY=-1
#     username: sa
#     password:
#   h2:
#     console:
#       enabled: ${H2_CONSOLE_ENABLED:false}
```

```kotlin
// build.gradle.kts - eliminar
// implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
// implementation("io.r2dbc:r2dbc-h2")
// runtimeOnly("com.h2database:h2")
```

---

#### M8. Configuracion multipart sin endpoints multipart

- **Archivo**: `application.yml:15-19`
- **Problema**: `spring.webflux.multipart.*` configurado pero no existe ningun endpoint que reciba `multipart/form-data`.
- **Solucion**: Eliminar la configuracion huerfana.

**Accion en application.yml:**
```yaml
# ELIMINAR este bloque:
# spring:
#   webflux:
#     multipart:
#       max-in-memory-size: 10MB
#       max-disk-usage-per-part: 10MB
#       max-parts: 10
```

---

#### M9. `DomainConfig` en capa incorrecta

- **Archivo**: `application/app/service/config/DomainConfig.java`
- **Problema**: La wiring de beans de dominio (casos de uso, servicios) esta en la capa de aplicacion. En Clean Architecture, la configuracion de beans deberia estar en infraestructura o en un modulo `bootstrap`.
- **Solucion**: Mover a `infrastructure.config.DomainConfig.java` (se soluciona junto con M6).

---

#### M10. `202 Accepted` en GET es enganoso

- **Archivo**: `infrastructure/entrypoints/rest/handler/ProductHandler.java:43`
- **Problema**: `ServerResponse.accepted()` (HTTP 202) indica que la solicitud fue aceptada para procesamiento asincrono diferido. Pero el handler espera sincronamente a que el Flux complete usando `.collectList()` (o incluso con streaming, el GET es sincrono). Es semanticamente incorrecto.
- **Solucion**: Usar `ServerResponse.ok()` (200) para procesamiento sincrono.

**Codigo solucion (junto con C7):**
```java
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_NDJSON)
    .body(results, FileUploadResult.class);
```

---

### BAJOS (8) - Pulido

---

#### B1. `toFileUploadResultError()` sin traceId en `SoapGatewayAdapter`

- **Archivo**: `infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:84-90`
- **Problema**: El metodo que construye el resultado de error no incluye `traceId` ni `errorCode`, dificultando la trazabilidad en logs.
- **Solucion**: Agregar traceId y mensaje. Este fix queda cubierto por la solucion de C4.

---

#### B2. Uso inconsistente de logging: `@Slf4j` vs `LoggerFactory.getLogger`

- **Archivos**: `S3GatewayAdapter` usa `@Slf4j`; `SoapGatewayAdapter`, `ProductHandler`, `ProductRestGatewayAdapter`, `SoapMapper`, `SoapEnvelopeWrapper`, `AbstractDocumentProcessingUseCase` crean logger manualmente.
- **Solucion**: Unificar a `@Slf4j` en todas las clases.

**Accion**: Reemplazar en cada clase:
```java
// DE:
private static final Logger log = LoggerFactory.getLogger(Xxx.class);
// A:
@Slf4j  // anotacion de Lombok en la clase
```
Luego eliminar el import de `org.slf4j.Logger` y `org.slf4j.LoggerFactory`.

---

#### B3. `Base64Utils` en dominio solo usado desde infraestructura

- **Archivo**: `domain/util/Base64Utils.java`
- **Problema**: La clase `Base64Utils` esta en `domain.util` pero el unico consumidor es `ProductRestGatewayAdapter` (infraestructura). Las utilidades de encoding/decoding no son logica de dominio.
- **Solucion**: Mover a `infrastructure.helpers` o crear un paquete `shared` si se espera que dominio tambien lo use en el futuro.

**Accion**: Mover archivo a `infrastructure/helpers/Base64Utils.java` y actualizar imports.

---

#### B4. `ProductHandler`: procesador invalido -> warning + default silencioso a SOAP

- **Archivo**: `infrastructure/entrypoints/rest/handler/ProductHandler.java:61-63`
- **Problema**: Si un cliente envia `?processor=invalid`, el handler loguea un warning y silenciosamente usa SOAP como default. El cliente jamas sabe que su parametro fue ignorado.
- **Solucion**: Retornar 400 Bad Request con mensaje claro.

**Codigo actual:**
```java
private AbstractDocumentProcessingUseCase getProcessor(String processorType) {
    if (ApiConstants.PROCESSOR_S3.equals(processorType)) { ... }
    if (!ApiConstants.PROCESSOR_SOAP.equals(processorType)) {
        log.warn("Unknown processor type '{}', defaulting to SOAP", processorType);
    }
    log.info("Using SOAP processor");
    return soapDocumentUseCase;
}
```

**Codigo solucion:**
```java
private AbstractDocumentProcessingUseCase getProcessor(String processorType) {
    return switch (processorType) {
        case ApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
        case ApiConstants.PROCESSOR_S3 -> {
            S3DocumentProcessingUseCase s3UseCase = s3DocumentUseCaseProvider.getIfAvailable();
            if (s3UseCase == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "S3 processor not available - enable 's3' profile");
            }
            yield s3UseCase;
        }
        default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Unknown processor type: '" + processorType + "'. Valid values: soap, s3");
    };
}
```

---

#### B5. `scripts/start-document-mock.sh` referencia clase inexistente `DocumentRestMock`

- **Archivo**: `scripts/start-document-mock.sh`
- **Problema**: El script intenta ejecutar `com.example.fileprocessor.mock.DocumentRestMock`, clase que ya no existe en el proyecto. Es un script huerfano de una iteracion anterior.
- **Solucion**: Eliminar `scripts/start-document-mock.sh` y `scripts/start-document-mock.bat`.

---

#### B6. `stop-mock.bat` tiene typo (caracter `e` suelto en linea 19)

- **Archivo**: `scripts/stop-mock.bat`
- **Problema**: Hay un caracter `e` solitario al inicio de una linea que rompe la ejecucion en Windows. Probablemente un artefacto de copy-paste.
- **Solucion**: Editar el archivo y eliminar el caracter `e` solitario.

---

#### B7. 5 pares de scripts .sh/.bat con logica 100% duplicada

- **Archivos**: `start-dev.*`, `verify-build.*`, `start-mock.*`, `stop-mock.*`, `start-document-mock.*`
- **Problema**: Cada par tiene exactamente la misma logica en bash y batch. Cualquier cambio requiere editar 2 archivos.
- **Solucion (baja prioridad)**: Reemplazar scripts por tareas de Gradle (`./gradlew startMocks`, `./gradlew stopMocks`) que son cross-platform por naturaleza.

**Ejemplo de Gradle task alternativa:**
```kotlin
// build.gradle.kts
tasks.register<JavaExec>("startSoapMock") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.example.fileprocessor.mock.PortableSoapMock")
    args = listOf("--port=9000", "--delay=0")
}
```

---

#### B8. Perfil `soap` vacio en `application.yml`

- **Archivo**: `application.yml:73-77`
- **Problema**: Declaracion de perfil `soap` con `---` y `on-profile: soap` pero sin ninguna propiedad.
- **Solucion**: Eliminar la seccion vacia. Spring reconoce el perfil `soap` por el solo hecho de activarlo con `--spring.profiles.active=soap`, no necesita declaracion vacia en YAML.

**Accion**: Eliminar lineas 73-77 de `application.yml`.

---

## DEUDA TECNICA DE TESTING

### Clases sin test (6 de alto riesgo)

| Clase | Prioridad | Solucion propuesta |
|---|---|---|
| `ProductHandler` | ALTA | Test con `WebTestClient` simulando `ObjectProvider` y verificando status codes y body |
| `S3GatewayAdapter` | ALTA | Test unitario mockeando `S3AsyncClient` con respuestas exitosas, timeout, errores S3 |
| `SoapGatewayAdapter` | ALTA | Test con `MockWebServer` simulando endpoint SOAP y verificando parsing |
| `DomainConfig` | MEDIA | Test de contexto Spring condicional verificando beans presentes/ausentes por perfil |
| `AwsConfig` | MEDIA | Test verificando creacion de `S3AsyncClient` con/sin credenciales |
| `ProductRoutes` | BAJA | Test con `WebTestClient` verificando que la ruta responde |

### Tests debiles existentes

| Test | Problema | Solucion |
|---|---|---|
| `DocumentFlowIntegrationTest` | `assertThat(true).isTrue()` - cero valor | Convertir en E2E real con `WebTestClient` + mocks levantados |
| Tests de entidades (`ProductTest`, `ProductDocumentTest`, etc.) | Solo verifican getters/setters Lombok | Eliminar (Lombok ya garantiza getters/setters). Si hay logica en builders, testear solo eso |
| `ProcessingResultCodesTest` | `assertNotNull` sobre constantes | Eliminar - no prueba comportamiento |
| `SoapDocumentProcessingUseCaseTest` | Solo verifica `implementationName()` | Agregar tests del pipeline real mockeando gateway y productGateway |
| `S3DocumentProcessingUseCaseTest` | Verificar si tiene tests reales o solo nombre | Igual que SOAP: mockear gateway y probar flujo completo |

---

## RESUMEN CUANTITATIVO

| Severidad | Cantidad |
|---|---|
| CRITICOS | 7 |
| ALTOS | 12 |
| MEDIOS | 10 |
| BAJOS | 8 |
| **Total hallazgos** | **37** |

| Categoria | Cantidad |
|---|---|
| Violaciones de arquitectura | 3 |
| Codigo muerto / sin uso | 8 |
| Code smells / mala practica | 12 |
| Configuracion incorrecta | 6 |
| Seguridad | 2 |
| Testing ausente/debil | 6 |

---

## PLAN DE ACCION RECOMENDADO

### Fase 1: Estabilizacion (C1-C7)
1. C1 - Crear `ValidationConfig` en dominio, romper dependencia infraestructura
2. C4 - Reemplazar `onErrorReturn` por `onErrorResume` selectivo en SOAP adapter
3. C5 - Agregar null guard para contenido en `S3GatewayAdapter`
4. C6 - Corregir prefijo `aws.s3.*` -> `app.aws.s3.*` en perfil S3
5. C7 - Eliminar `.collectList()` y usar streaming en `ProductHandler`

### Fase 2: Limpieza de codigo (A1-A12, M1-M10)
6. A2, A3 - Eliminar `ProductStatus` entero y valores huerfanos de `DocumentStatus`
7. A1 - Extraer `handleUploadError()` en `AbstractDocumentProcessingUseCase`
8. A4 - Unificar constante `"message-id"` usando solo la de dominio
9. A10, M4 - Limpiar `S3Properties` y `SoapProperties` (defaults vs @Min, campos huerfanos)
10. M1, M2, M3 - Eliminar `Base64Utils.decode()`, catch redundante en `SoapMapper`
11. M7, M8 - Eliminar config R2DBC/H2 y multipart sin uso
12. M5, M6, M9 - Mover `DomainConfig` a `infrastructure.config`, arreglar paquete
13. B1-B8 - Correcciones menores (logging, scripts, perfiles vacios)

### Fase 3: Testing
14. Tests para `ProductHandler` con `WebTestClient`
15. Tests para `S3GatewayAdapter` mockeando `S3AsyncClient`
16. Tests para `SoapGatewayAdapter` con `MockWebServer`
17. Eliminar tests de entidades Lombok que no prueban comportamiento
18. E2E test real que levante mocks y ejercite el flujo completo via HTTP

### Fase 4: Seguridad y produccion
19. A5 - ELIMINAR Resilience4j del proyecto (dependencias y configuraciones)
20. A6 - Completar `application-prod.yml` con todas las propiedades
21. M10 - Cambiar 202 -> 200 en handler

---

## Verificacion

- `./gradlew build` debe pasar sin errores
- `./gradlew test` todos los tests pasan
- `./gradlew jacocoTestReport` cobertura > 75%
- `./gradlew pitest` mutation coverage > 60%
- Levantar servicio con `./start-dev.sh` y probar con `curl "http://localhost:8080/api/v1/products?processor=soap"`
