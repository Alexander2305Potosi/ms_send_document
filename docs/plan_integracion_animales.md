# Plan de Integración de Procesamiento de Documentos de Animales (Animal)

Este documento define el diseño arquitectónico y plan de implementación para el nuevo flujo de negocio de procesamiento diario (`type_job = daily`) y caso de uso Animal (`use-case = animal`). El flujo consiste en leer información de animales desde una base de datos secundaria, consultar servicios REST externos para obtener la estructura de directorios, filtrar documentos basados en el origen (`Source` in `[1, 2, 4]`), descargarlos de la API REST de productos existente y enviarlos mediante el canal SOAP actual, registrando la trazabilidad en un esquema dedicado.

---

## 1. Diseño de Base de Datos y Trazabilidad

### Base de Datos Existente y Mapeo R2DBC
El servicio actualmente utiliza un motor H2 (en memoria para desarrollo/test) configurado con R2DBC. Los Repositorios usan Spring Data R2DBC.

### Nuevas Tablas en Esquema `schemAnimals`
Dado que la base de datos física puede contener múltiples esquemas, mapearemos las entidades R2DBC agregando explícitamente el prefijo de esquema en la anotación `@Table`.

1. **Tabla de Origen**: `schemAnimals.animals_maestro`
   Representa la tabla maestra de donde se extraerán los animales para procesamiento diario.
   *(La trazabilidad se registrará en la tabla `historico_documentos` existente, reutilizando la lógica del caso de uso base)*

---

## 2. Arquitectura Propuesta (Clean Architecture)

Seguiremos la estructura actual del proyecto basada en Puertos y Adaptadores (Arquitectura Hexagonal):

```mermaid
graph TD
    Entrypoint["ProductHandler / Router"] -->|processDailyAnimal| UseCase[AnimalDocumentProcessingUseCase]
    Entrypoint -->|getDailyAnimalStatus| StatusUseCase[GetStatusUseCase]

    UseCase --> PortDb[AnimalRepository]
    UseCase --> PortRest[AnimalRestGateway]
    UseCase --> PortProd[ProductRestGateway]
    UseCase --> PortSoap[SoapGateway]
    UseCase --> PortHom[HomologationRepository]

    StatusUseCase --> PortDb

    PortDb -->|Implementado por| AdapterDb[AnimalR2dbcAdapter]
    PortRest -->|Implementado por| AdapterRest[AnimalRestGatewayAdapter]

    AdapterDb -->|"SQL/R2DBC"| Db[(schemAnimals DB)]
    AdapterRest -->|"WebClient + timeout + errorMapper"| ExtAPI[External REST API]
```

---

## 3. Código Fuente Detallado (Propuesta de Implementación)

### 3.1. Capa de Dominio (Domain)

#### Entidades

##### `com/example/fileprocessor/domain/entity/animal/AnimalMaestro.java`
```java
package com.example.fileprocessor.domain.entity.animal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnimalMaestro {
    Long id;
    String name;
    String category;
}
```

##### `com/example/fileprocessor/infrastructure/drivenadapters/restclient/dto/DirectoryNode.java` *(Movido a infraestructura — es detalle interno del Adapter)*
```java
package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
/**
 * DTO interno del Adapter REST para deserializar la respuesta del árbol de directorios.
 * No es entidad de dominio — la conversión a Document ocurre dentro del Adapter.
 */
public class DirectoryNode {
    String id;
    String name;
    Integer source;
    String productId;
    String businessDocumentId;
    List<DirectoryNode> children;
}
```



#### Puertos de Salida (Ports Out)

##### `com/example/fileprocessor/domain/port/out/AnimalRepository.java`
```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.animal.AnimalMaestro;
import com.example.fileprocessor.domain.entity.animal.AnimalHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AnimalRepository {
    Flux<AnimalMaestro> findAllAnimals();

}
```

##### `com/example/fileprocessor/domain/port/out/AnimalRestGateway.java`
```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.Document;
import reactor.core.publisher.Flux;

/**
 * Puerto de salida para consultar documentos pendientes de animales.
 * La complejidad de obtener directorios, aplanar el árbol y filtrar
 * por Source queda encapsulada en el Adapter de infraestructura.
 */
public interface AnimalRestGateway {
    Flux<Document> getPendingDocumentsForAnimal(Long animalId);
}
```

#### Caso de Uso (UseCase)

##### `com/example/fileprocessor/domain/usecase/AnimalDocumentProcessingUseCase.java`
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Caso de uso específico para las reglas de negocio de carga (upload) de Animales.
 * Se mantiene limpio y enfocado al igual que S3 y Soap, definiendo solo el canal de envío.
 */
public class AnimalDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final AnimalRepository animalRepository;
    private final AnimalRestGateway animalRestGateway;
    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public AnimalDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator,
            String tempDirPath,
            AnimalRepository animalRepository,
            AnimalRestGateway animalRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository) {
        super(persistencePort, productRestGateway, documentValidator, tempDirPath);
        this.animalRepository = animalRepository;
        this.animalRestGateway = animalRestGateway;
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
        return homologationRepository.resolve(history)
                .flatMapMany(homologation -> {
                    FileUploadRequest uploadReq = FileUploadRequest.from(history, null, homologation);
                    return soapGateway.send(uploadReq);
                });
    }

    @Override
    protected String implementationName() {
        return "Animal";
    }
}
```

#### 2.2.3. Nuevo método limpio en `AbstractDocumentProcessingUseCase`
**Archivo a modificar:** `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

Se deben inyectar las dependencias `AnimalRepository` y `AnimalRestGateway` en el constructor de la clase base (los hijos S3 y SOAP inyectarán instancias o nulos según su configuración).

La lógica de aplanar, filtrar e iterar el árbol de directorios se delegará completamente al Adapter (Infraestructura), dejando en la clase abstracta un único método limpio para orquestar:

```java
    /**
     * Orquesta el flujo diario de Animales de forma limpia.
     * Toda la complejidad de aplanar y filtrar el árbol reside en el Adapter del Gateway.
     */
    public Flux<FileUploadResponse> executeAnimalProcessing() {
        LOGGER.info("Iniciando procesamiento diario Animal...");
        return animalRepository.findAllAnimals()
                .flatMap(animal -> animalRestGateway.getPendingDocumentsForAnimal(animal.getId()) // Retorna un Flux<Document> ya aplanado y filtrado
                        .flatMap(doc -> {
                            String traceId = "Animal-" + animal.getId() + "-" + doc.getDocumentId();
                            return processWithTracking(doc, traceId); // processWithTracking guarda el historial en historico_documentos
                        })
                );
    }
```

---

### 3.2. Capa de Infraestructura (Infrastructure)

#### Entidades R2DBC (Mapeo de base de datos)

##### `com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/AnimalMaestroEntity.java`
```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.*;

@Table("schemAnimals.animals_maestro")
@Getter
// Sin @Setter — entidad de solo lectura (fuente de datos maestra)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnimalMaestroEntity {
    @Id
    private Long id;

    @Column("name")
    private String name;

    @Column("category")
    private String category;
}
```



#### Adaptador R2DBC

##### `com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/AnimalR2dbcAdapter.java`
```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.animal.AnimalMaestro;
import com.example.fileprocessor.domain.entity.animal.AnimalHistory;
import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalHistoryEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalMaestroEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalHistoryRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalMaestroRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnimalR2dbcAdapter implements AnimalRepository {

    private final AnimalMaestroRepository maestroRepository;

    @Override
    public Flux<AnimalMaestro> findAllAnimals() {
        return maestroRepository.findAll()
                .map(entity -> AnimalMaestro.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .category(entity.getCategory())
                        .build());
    }

}
```

#### Adaptador REST de API de Animales y Directorios

##### Nuevo record de propiedades: `com/example/fileprocessor/infrastructure/entrypoints/rest/config/AnimalRestProperties.java`
```java
package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.animal-rest")
public record AnimalRestProperties(
    @NotBlank String endpoint,
    @NotBlank String animalDirectoryPath,
    @NotBlank String directoryTreePath,
    @Min(1) int timeoutSeconds
) {}
```

##### `com/example/fileprocessor/infrastructure/drivenadapters/restclient/AnimalRestGatewayAdapter.java`
```java
package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.AdapterErrorMapper;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.DirectoryNode;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.AnimalRestProperties;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class AnimalRestGatewayAdapter implements AnimalRestGateway {

    private static final Logger LOGGER = Logger.getLogger(AnimalRestGatewayAdapter.class.getName());
    private static final Set<Integer> VALID_SOURCES = Set.of(1, 2, 4);

    private final WebClient webClient;
    private final AnimalRestProperties properties;

    public AnimalRestGatewayAdapter(WebClient.Builder webClientBuilder, AnimalRestProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.webClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Flux<Document> getPendingDocumentsForAnimal(Long animalId) {
        return getDirectoryIdByAnimalId(animalId)
                .flatMap(this::getDirectoryTree)
                .flatMapMany(tree -> Flux.fromIterable(flattenAndFilter(tree)))
                .map(node -> Document.builder()
                        .documentId(node.getBusinessDocumentId())
                        .productId(node.getProductId())
                        .nombreDocumento(node.getName())
                        .esZip(false)
                        .casoUso("Animal")
                        .retryCount(0)
                        .build())
                .onErrorResume(error -> {
                    LOGGER.log(Level.SEVERE, "Error obteniendo documentos para animalId={0}: {1}",
                            new Object[]{animalId, error.getMessage()});
                    return Flux.empty();
                });
    }

    private Mono<String> getDirectoryIdByAnimalId(Long animalId) {
        return webClient.get()
                .uri(properties.animalDirectoryPath(), animalId)
                .retrieve()
                .bodyToMono(DirectoryResponse.class)
                .map(DirectoryResponse::getDirectoryId)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .doOnError(e -> LOGGER.log(Level.SEVERE, "Error obteniendo directoryId para animalId={0}: {1}",
                        new Object[]{animalId, e.getMessage()}));
    }

    private Mono<DirectoryNode> getDirectoryTree(String directoryId) {
        return webClient.get()
                .uri(properties.directoryTreePath(), directoryId)
                .retrieve()
                .bodyToMono(DirectoryNode.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .doOnError(e -> LOGGER.log(Level.SEVERE, "Error obteniendo arbol para directoryId={0}: {1}",
                        new Object[]{directoryId, e.getMessage()}));
    }

    private List<DirectoryNode> flattenAndFilter(DirectoryNode root) {
        List<DirectoryNode> result = new ArrayList<>();
        traverse(root, result);
        return result;
    }

    private void traverse(DirectoryNode node, List<DirectoryNode> result) {
        if (node == null) return;
        if (node.getSource() != null && VALID_SOURCES.contains(node.getSource())) {
            result.add(node);
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> traverse(child, result));
        }
    }

    @lombok.Data
    private static class DirectoryResponse {
        private String directoryId;
    }
}
```

---

### 3.3. Rutas y Controladores (WebFlux endpoints)

Para habilitar este endpoint, mantendremos la estructura existente agregando la nueva lógica al `ProductHandler` y a las rutas.

#### Cambio en `ProductRoutes.java`
Agregar el nuevo endpoint que detecte los parámetros `type_job = daily` y lance el flujo de procesamiento.
Se agregará una ruta bajo `app.paths.API_V1_PRODUCTS` (o una variante similar):

```java
    @Bean
    public RouterFunction<ServerResponse> processDailyAnimalProducts(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET(pathProperties.API_V1_PRODUCTS_DAILY_ANIMAL()), handler::processDailyAnimalProducts)
        );
    }

    @Bean
    public RouterFunction<ServerResponse> processDailyAnimalStatusRoute(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET(pathProperties.API_V1_PRODUCTS_PROCESS_STATUS_DAILY()), handler::getDailyAnimalProcessStatus)
        );
    }
```

#### Modificación del constructor en `ProductHandler.java`
**Archivo a modificar:** `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java`

Agregar `AnimalDocumentProcessingUseCase` como 5to parámetro del constructor existente:
```java
    private final AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase; // NUEVO

    public ProductHandler(
            SoapDocumentProcessingUseCase soapDocumentUseCase,
            ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider,
            SyncDocumentsUseCase syncDocumentsUseCase,
            GetStatusUseCase getStatusUseCase,
            AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase) { // NUEVO
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
        this.syncDocumentsUseCase = syncDocumentsUseCase;
        this.getStatusUseCase = getStatusUseCase;
        this.animalDocumentProcessingUseCase = animalDocumentProcessingUseCase; // NUEVO
    }
```

#### Nuevos métodos en `ProductHandler.java`
```java

    public Mono<ServerResponse> processDailyAnimalProducts(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        
        Context context = Context.of(
            TYPE_JOB, "daily",
            HEADER_TRACE_ID, headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString()),
            HEADER_USE_CASE, "animal"
        );

        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(HEADER_TRACE_ID);
            LOGGER.log(Level.INFO, "Starting Daily Animal Processing, traceId: {0}", traceId);

            animalDocumentProcessingUseCase.executeAnimalProcessing()
                .doOnNext(response -> LOGGER.log(Level.INFO, "Animal Document Processed: file={0}, success={1}",
                    new Object[]{response.getFilename(), response.isSuccess()}))
                .doOnError(error -> LOGGER.log(Level.SEVERE, "Animal Daily processing failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnComplete(() -> LOGGER.log(Level.INFO, "Animal Daily processing completed for traceId: {0}", traceId))
                .contextWrite(ctx)
                .subscribe();

            return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("status", "OK", "message", "Daily Animal processing initiated"));
        }).contextWrite(context);
    }

    public Mono<ServerResponse> getAnimalProcessStatus(ServerRequest request) {
        return getStatusUseCase.getProcessStatus("Animal")
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }
```

#### Modificación en `DomainConfig.java`
**Archivo a modificar:** `src/main/java/com/example/fileprocessor/application/service/config/DomainConfig.java`

Agregar el bean de `AnimalDocumentProcessingUseCase` (con `ProcessorsProperties.animal()` para las reglas de negocio) y actualizar el bean de `GetStatusUseCase` para recibir `AnimalRepository`:

```java
    @Bean
    public AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            AnimalRepository animalRepository,
            AnimalRestGateway animalRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            ProcessorsProperties properties) {
        return new AnimalDocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            new RulesBussinesService(properties.animal()),  // Reutiliza RulesBussinesService existente
            properties.zipTempDir(),
            animalRepository,
            animalRestGateway,
            soapGateway,
            homologationRepository
        );
    }

    // El bean GetStatusUseCase permanece intacto ya que reutilizaremos el método getProcessStatus("Animal")
```
> **Nota:** Se debe agregar `ProcessorConfig animal` al record `ProcessorsProperties` existente y configurar `app.processors.animal.max-file-size-bytes` y `app.processors.animal.filename-pattern` en `application.yml`.

---

## 4. Estrategia de Pruebas y Cobertura

Para garantizar la estabilidad y confiabilidad de esta nueva implementación, se ejecutará una estrategia de pruebas basada en pruebas unitarias y de mutación (Mutation Testing) siguiendo las configuraciones existentes del proyecto.

### 4.1. Pruebas Unitarias

#### Escenarios de Prueba para `AnimalDocumentProcessingUseCaseTest`
Se deben crear pruebas unitarias exhaustivas utilizando JUnit 5 y Mockito para cubrir los siguientes escenarios:
- **Flujo Exitoso Completo**: Validar la obtención de la lista de animales, el ID de directorio y el árbol de carpetas. Probar el aplanamiento correcto del árbol y verificar que se procesan y envían a SOAP solo los documentos con `Source` igual a `1`, `2` o `4`.
- **Filtro de Nodos Inactivos**: Validar que los nodos del directorio con `Source` diferente de `1, 2, 4` (por ejemplo, `3`, `5` o `null`) sean explícitamente ignorados y no generen llamadas de descarga ni envíos SOAP.
- **Manejo de Errores en API Externa (REST)**: Validar el comportamiento de resiliencia del flujo reactivo si la llamada para obtener el árbol de directorios retorna un error (HTTP 500, Timeout, etc.), asegurando que el caso de uso registre la trazabilidad en estado `ERROR` en la base de datos sin romper el flujo de otros animales.
- **Fallo en Envío SOAP**: Validar que si el servicio SOAP falla para un documento específico, se persista la trazabilidad con estado `FAILED` y su correspondiente mensaje de error, mientras que el resto de los documentos del mismo animal o de otros animales continúe procesándose.

#### Escenarios de Prueba para `GetStatusUseCaseTest` (Endpoint de Control)
- **Monitoreo - Sin Registros**: Validar que si no se han procesado animales en el día actual, retorne `STATUS_COMPLETED` (estado por defecto).
- **Monitoreo - En Progreso**: Validar que si existe al menos un registro hoy con estado `PENDING` o `IN_PROGRESS`, el estado general retornado sea `STATUS_IN_PROGRESS`.
- **Monitoreo - Errores**: Validar que si existen registros con estado de error hoy y ninguno está pendiente, el estado general retornado sea `STATUS_ERROR`.

---

### 4.2. Pruebas de Mutación (Mutation Testing)

El proyecto cuenta con el plugin **Pitest** (`info.solidsoft.pitest`) integrado y configurado en el archivo `build.gradle.kts` para evaluar la efectividad y calidad de los tests unitarios mediante la inyección de mutantes en el bytecode.

#### Configuración del Mutation Testing
Las nuevas clases deberán alinearse a los límites y umbrales definidos en la configuración de Pitest del proyecto:
- **Clases Objetivo (targetClasses)**: Pitest analizará el dominio (`com.example.fileprocessor.domain.*`) y los adaptadores (`com.example.fileprocessor.infrastructure.drivenadapters.*`). Las clases del caso de uso Animal y el adaptador R2DBC de animales serán evaluadas automáticamente.
- **Umbral de Mutación (mutationThreshold)**: Configurado en **60%**. Se requiere que al menos el 60% de los mutantes generados en las nuevas clases sean "eliminados" (killed) por las pruebas unitarias.
- **Umbral de Cobertura de Líneas (coverageThreshold)**: Configurado en **80%**.
- **Mutadores Activos**: Se evaluarán mutaciones críticas como:
  - `NEGATE_CONDITIONALS` e `invert_negs` (invertir condiciones lógicas).
  - `REMOVE_CONDITIONALS_EQUAL_IF` y `REMOVE_CONDITIONALS_ORDER_IF` (eliminar condiciones en bloques IF).
  - `MATH` y `REMOVE_INCREMENTS` (mutaciones matemáticas y de incrementos).
  - `VOID_METHOD_CALLS` y `NON_VOID_METHOD_CALLS` (eliminar llamadas a métodos).

#### Comando para Ejecutar las Pruebas de Mutación
Para ejecutar el análisis de mutación localmente y verificar que las nuevas clases cumplen con los umbrales de cobertura y supervivencia de mutantes, se debe ejecutar:

```bash
./gradlew pitest
```

El reporte detallado en formato HTML con los mutantes sobrevivientes estará disponible en:
`build/reports/pitest/index.html`

Se deberá iterar sobre el reporte agregando casos de prueba límite (boundary testing) y validaciones condicionales en las pruebas unitarias hasta eliminar todos los mutantes sobrevivientes relevantes y superar los umbrales exigidos.


