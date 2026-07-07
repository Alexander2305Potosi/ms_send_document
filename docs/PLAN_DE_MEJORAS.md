# Plan de Acción y Mejoras: `file-processor-service`

Este documento detalla el plan de implementación para resolver los hallazgos críticos y altos identificados en la revisión de código. Para cada hallazgo se incluye el fragmento de código con la etiqueta **[MODIFICADO]** o **[NUEVO]** para detallar exactamente cómo se aplicará el cambio.

---

## 🔴 CRÍTICOS

### [C-01] Corregir typo que causa error de compilación
**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentR2dbcAdapter.java`  
**Acción:** **[MODIFICADO]** Se debe corregir la palabra `fileprocessity` por `fileprocessor`.

```java
// --- ANTES ---
java.time.LocalDate end = com.example.fileprocessity.infrastructure.entrypoints.rest.constants.ApiConstants.parseDateOrToday(dateEndVal);

// --- DESPUÉS [MODIFICADO] ---
java.time.LocalDate end = com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.parseDateOrToday(dateEndVal);
```

### [C-02] Centralizar constante `MAX_RETRIES`
**Archivos:** `AbstractDocumentProcessingUseCase.java`, `DocumentHistoryFactory.java`, `ProcessingResultCodes.java`  
**Acción:** **[NUEVO]** Agregar constante centralizada. **[MODIFICADO]** Eliminar duplicados y referenciar la central.

```java
// --- DESPUÉS [NUEVO] (en ProcessingResultCodes.java) ---
public static final int MAX_RETRIES = 3;

// --- DESPUÉS [MODIFICADO] (en AbstractDocumentProcessingUseCase.java y DocumentHistoryFactory.java) ---
// Eliminar: private static final int MAX_RETRIES = 3;

// Modificar uso en DocumentHistoryFactory.java:
} else if (currentRetry < ProcessingResultCodes.MAX_RETRIES && responses.stream().anyMatch(r -> ProcessingResultCodes.isTransient(r.getSyncStatus()))) {
```

---

## 🟠 ALTOS

### [A-01] Corregir `doOnDiscard` que nunca se dispara
**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`  
**Acción:** **[MODIFICADO]** Reemplazar `doOnDiscard` por `switchIfEmpty` para capturar correctamente cuando el `filter` descarta el registro.

```java
// --- ANTES ---
return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
        .filter(rows -> rows > 0)
        .doOnDiscard(Long.class,
                unused -> LOGGER.log(Level.WARNING,
                        "[TraceID: {0}] Document {1} is already being processed or locked.",
                        new Object[] { traceId, doc.getDocumentId() }))
        .flatMap(unused -> downloadDocument(baseHistory)

// --- DESPUÉS [MODIFICADO] ---
return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
        .filter(rows -> rows > 0)
        .switchIfEmpty(Mono.defer(() -> {
            LOGGER.log(Level.WARNING,
                    "[TraceID: {0}] Document {1} is already being processed or locked.",
                    new Object[] { traceId, doc.getDocumentId() });
            return Mono.empty();
        }))
        .flatMap(unused -> downloadDocument(baseHistory)
```

### [A-02] Eliminar cursor inactivo en Sincronización
**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCase.java`  
**Acción:** **[MODIFICADO]** Limpiar el código muerto del cursor, ya que `getAllProducts()` no lo soporta.

```java
// --- ANTES ---
return productMasterRepository.getAllProducts()
        .contextWrite(c -> lastProductId.isEmpty()
                ? c
                : c.put("last_product_id", lastProductId))
        .flatMap(product -> syncDocumentsForProduct(product, useCase))

// --- DESPUÉS [MODIFICADO] ---
return productMasterRepository.getAllProducts()
        .flatMap(product -> syncDocumentsForProduct(product, useCase))
```

### [A-03] Evitar mutación de objetos en pipeline reactivo
**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCase.java`  
**Acción:** **[MODIFICADO]** Usar el patrón `Builder` en lugar de `setters` sobre el objeto compartido.

```java
// --- ANTES ---
doc.setUseCase(useCase);
doc.setOriginFolder(product.getOriginFolder());
doc.setOriginCountry(product.getOriginCountry());
doc.setSucursal(sucursal);
if (exists) {
    doc.setState(ProcessingResultCodes.ERR_DUPLICATED_DOC.name());
    doc.setSyncMessage(ProcessingResultCodes.ERR_DUPLICATED_DOC.value());
} else {
    doc.setState(ProcessingResultCodes.PENDING.name());
}
return documentRepository.save(doc);

// --- DESPUÉS [MODIFICADO] ---
Document docToSave = doc.builder()
    .productId(doc.getProductId())
    .documentId(doc.getDocumentId())
    .name(doc.getName())
    .isZip(doc.getIsZip())
    .useCase(useCase)
    .originFolder(product.getOriginFolder())
    .originCountry(product.getOriginCountry())
    .sucursal(sucursal)
    .state(exists ? ProcessingResultCodes.ERR_DUPLICATED_DOC.name() : ProcessingResultCodes.PENDING.name())
    .syncMessage(exists ? ProcessingResultCodes.ERR_DUPLICATED_DOC.value() : null)
    .build();
return documentRepository.save(docToSave);
```

### [A-04] Clasificación de `UNKNOWN_ERROR` como No-Transitorio
**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/ProcessingResultCodes.java`  
**Acción:** **[MODIFICADO]** Quitar `UNKNOWN_ERROR` de los errores transitorios para evitar bucles infinitos por bugs reales.

```java
// --- ANTES ---
public static boolean isTransient(String code) {
    // ...
    return switch (res) {
        case BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE, UNKNOWN_ERROR, RETRYABLE_ERROR -> true;
        default -> false;
    };
}

// --- DESPUÉS [MODIFICADO] ---
public static boolean isTransient(String code) {
    // ...
    return switch (res) {
        case BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE, RETRYABLE_ERROR -> true;
        default -> false;
    };
}
```

### [A-05] Eliminar hilo de bloqueo innecesario (`publishOn`)
**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java`  
**Acción:** **[MODIFICADO]** Retirar el `publishOn(boundedElastic())` en peticiones WebClient.

```java
// --- ANTES ---
return webClient.get()
        // ...
        .retrieve()
        .bodyToFlux(ProductDocumentResponse.class)
        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
        .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .map(doc -> mapToDocument(product.getProductId(), doc))

// --- DESPUÉS [MODIFICADO] ---
return webClient.get()
        // ...
        .retrieve()
        .bodyToFlux(ProductDocumentResponse.class)
        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
        .map(doc -> mapToDocument(product.getProductId(), doc))
```

### [A-06] Corregir bloqueos en `DbInitializer`
**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/config/DbInitializer.java`  
**Acción:** **[MODIFICADO]** Arreglar problema del `@Qualifier` con Lombok y evitar inicialización bloqueante manual en un contexto reactivo, delegando en un `@Bean`.

```java
// --- ANTES ---
@RequiredArgsConstructor
public class DbInitializer {
    private final ConnectionFactory connectionFactory;
    
    @Qualifier("masterConnectionFactory")
    private final ConnectionFactory masterConnectionFactory;

    @PostConstruct
    public void init() {
        System.out.println("DEBUG: Executing DB Initializer...");
        // ...
        localInitializer.afterPropertiesSet();
    }
}

// --- DESPUÉS [MODIFICADO] ---
@Configuration
public class DbInitializer {

    // Se usan Beans en lugar de @PostConstruct para no bloquear el inicio de la app
    @Bean
    public ConnectionFactoryInitializer localDbInitializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }

    @Bean
    public ConnectionFactoryInitializer masterDbInitializer(@Qualifier("masterConnectionFactory") ConnectionFactory masterConnectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(masterConnectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("master-schema.sql")));
        return initializer;
    }
}
```

---

## 🟢 LIMPIEZA DE CÓDIGO (Sin Uso)

### [L-01] Import sin uso en `DocumentHistoryR2dbcAdapter`
**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentHistoryR2dbcAdapter.java`  
**Acción:** **[MODIFICADO]** Eliminar la importación no utilizada de la entidad `Document`.

```java
// --- ANTES ---
import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;

// --- DESPUÉS [MODIFICADO] ---
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
```

### [L-02] Interfaz/Puerto Legacy sin uso (`DocumentHistoryRepository`)
**Archivo:** `src/main/java/com/example/fileprocessor/domain/port/out/DocumentHistoryRepository.java`  
**Acción:** **[MODIFICADO]** Eliminar el archivo por completo.  
**Motivo:** El puerto define `Mono<Void> saveHistory(Document doc, DocumentHistoryDTO history);` pero la arquitectura actual utiliza `DocumentPersistenceGateway` para este propósito y ninguna clase implementa ni inyecta este puerto. Es código muerto.

### [L-03] Lógica y método sin uso: `findLastProcessedProductIdInRange`
**Archivos:** `domain/port/out/DocumentRepository.java` y `DocumentR2dbcAdapter.java`  
**Acción:** **[MODIFICADO]** Eliminar la declaración en el puerto y la implementación en el adaptador.  
**Motivo:** El método se pensó para usarse en el caso de uso `SyncDocumentsUseCase`, pero este último nunca lo llama. La lógica de reanudación está incompleta y este método de base de datos nunca se invoca, sumando deuda técnica.

```java
// --- ANTES (en DocumentRepository.java) ---
    /** Obtiene el último ID de producto procesado dentro del rango de fechas leído del contexto reactivo */
    Mono<String> findLastProcessedProductIdInRange();

// --- DESPUÉS [MODIFICADO] ---
    // (Se elimina el método de la interfaz y su implementación en DocumentR2dbcAdapter)
```

---

## 🟡 MEDIOS (Destacados)

### [M-05] Optimización del enum `ProcessingResultCodes` (Quitar Try-Catch en lógica de negocio)
**Archivo:** `src/main/java/com/example/fileprocessor/domain/usecase/ProcessingResultCodes.java`  
**Acción:** **[NUEVO/MODIFICADO]** Usar un `Set` en lugar de Capturar excepciones por rendimiento.

```java
// --- ANTES ---
public static boolean isBusinessRule(String code) {
    if (code == null) return false;
    try {
        ProcessingResultCodes res = valueOf(code);
        return switch (res) { ... };
    } catch (IllegalArgumentException e) {
        return false;
    }
}

// --- DESPUÉS [MODIFICADO] ---
private static final java.util.Set<String> BUSINESS_RULES = java.util.Set.of(
    INVALID_BASE64.name(), EMPTY_CONTENT.name(), DECOMPRESSION_ERROR.name(), 
    SIZE_EXCEEDED.name(), PATTERN_MISMATCH.name(), SKIPPED.name(), 
    ERR_DUPLICATED_DOC.name(), BUSINESS_REJECTION.name()
);

public static boolean isBusinessRule(String code) {
    if (code == null) return false;
    return BUSINESS_RULES.contains(code);
}
```

### [M-11] Reusar el bean de `ObjectMapper`
**Archivo:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/rule/JsonRuleEvaluator.java`  
**Acción:** **[MODIFICADO]** Inyectar la dependencia.

```java
// --- ANTES ---
public class JsonRuleEvaluator {
    private final ObjectMapper mapper = new ObjectMapper();
    public JsonRuleEvaluator() {
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }
}

// --- DESPUÉS [MODIFICADO] ---
public class JsonRuleEvaluator {
    private final ObjectMapper mapper;
    
    public JsonRuleEvaluator(ObjectMapper mapper) {
        this.mapper = mapper;
        // La registración de módulos debe hacerse en el bean global MapperConfig
    }
}
```
