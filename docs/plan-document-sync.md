# Plan: Nueva tabla `documento` + `historico_documentos` con reintentos

## Context

API_V1_PRODUCTS_SYNC (`POST /api/v1/products/sync`) actualmente solo sincroniza productos a la tabla `productos`.
API_V1_PRODUCTS (`GET /api/v1/products`) actualmente obtiene productos pendientes por fecha de carga, y por cada documento: fetch desde REST → decompress → validate → upload → save history.

**Necesidades:**
1. Nueva tabla `documento` como tabla principal de documentos — centraliza toda la información del documento
2. Nueva tabla `historico_documentos` para guardar trazabilidad de envíos a endpoints externos (SOAP, S3, etc.)
3. El campo `retry` va en `historico_documentos` (trackea reintentos por caso de uso)
4. La tabla `historico_documentos` debe registrar qué caso de uso (SOAP, S3, etc.) envió el documento
5. Eliminar tablas `productos` e `historico_documentos` antiguas

---

## Archivos Existentes Críticos (SOLO LECTURA)

| Archivo | Propósito |
|---------|-----------|
| `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java` | Entrypoint REST de ambos endpoints |
| `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java` | Caso de uso base — `executePendingDocuments()` procesa productos por loadDate |
| `src/main/java/com/example/fileprocessor/domain/usecase/SyncProductsUseCase.java` | Caso de uso sync actual — solo guarda productos |
| `src/main/java/com/example/fileprocessor/domain/entity/ProductDocumentFile.java` | Record retornado por `getDocument()` — **requiere agregar campo `productId`** |
| `src/main/java/com/example/fileprocessor/domain/port/out/ProductRestGateway.java` | Interfaz: `getAllProducts()`, `getDocument(productId, documentId)` |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java` | Adaptador REST implementando ProductRestGateway |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/dto/ProductDocumentResponse.java` | DTO — solo tiene: documentId, filename, content, contentType, size, isZip, origin, pais |
| `src/main/java/com/example/fileprocessor/domain/port/out/RulesBussinesGateway.java` | Interfaz de validación |
| `src/main/java/com/example/fileprocessor/domain/util/ZipDecompressor.java` | Utilidad de descompresión ZIP |
| `src/main/java/com/example/fileprocessor/domain/usecase/ProcessingResultCodes.java` | Enum de códigos de error |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/ProductEntity.java` | Entidad de tabla `productos` — **será eliminada** |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentHistoryEntity.java` | Entidad de tabla `historico_documentos` — **será eliminada** |
| `src/main/java/com/example/fileprocessor/domain/port/out/DocumentHistoryRepository.java` | Puerto para historico — **será eliminado/reemplazado** |
| `src/main/java/com/example/fileprocessor/domain/port/out/ProductRepository.java` | Puerto para productos — **será eliminado** |

---

## Cambios

### 1. Migration SQL — ELIMINAR tablas existentes y crear nuevas

```sql
-- Eliminar tablas anteriores
DROP TABLE IF EXISTS historico_documentos;
DROP TABLE IF EXISTS productos;

-- Crear tabla documento (tabla principal centralizada)
CREATE TABLE documento (
    id SERIAL PRIMARY KEY,
    id_document VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    doc_key VARCHAR(255),
    name VARCHAR(255),
    owner VARCHAR(255),
    path TEXT,
    status VARCHAR(50) NOT NULL,
    version_contract VARCHAR(50),
    state VARCHAR(100) NOT NULL,
    error_message TEXT,
    is_zip BOOLEAN DEFAULT FALSE,
    parent_zip_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documento_status ON documento(status);
CREATE INDEX idx_documento_product_id ON documento(product_id);
CREATE INDEX idx_documento_state ON documento(state);
CREATE INDEX idx_documento_parent_zip ON documento(parent_zip_name);

-- Crear tabla historico_documentos (trazabilidad de envíos)
CREATE TABLE historico_documentos (
    id SERIAL PRIMARY KEY,
    document_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    use_case VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    retry INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_historico_document_id ON historico_documentos(document_id);
CREATE INDEX idx_historico_product_id ON historico_documentos(product_id);
CREATE INDEX idx_historico_use_case ON historico_documentos(use_case);
CREATE INDEX idx_historico_status ON historico_documentos(status);
```

### 2. Nueva Entidad de Dominio: `Document.java`
**Ruta:** `src/main/java/com/example/fileprocessor/domain/entity/Document.java`

```java
package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record Document(
    Long id,
    String documentId,      // id_document
    String productId,       // product_id (string)
    String docKey,
    String name,
    String owner,
    String path,
    String status,          // PENDING, SENT, FAILED
    String versionContract,
    String state,           // SYNCED, IN_PROGRESS, PROCESSED, FAILED
    String errorMessage,
    Boolean isZip,          // indica si es un archivo ZIP original
    String parentZipName,   // nombre del ZIP padre (si fue descomprimido)
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

### 3. Nueva Entidad R2dbc: `DocumentEntity.java`
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentEntity.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "documento")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_document", nullable = false)
    private String documentId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "doc_key")
    private String docKey;

    @Column(name = "name")
    private String name;

    @Column(name = "owner")
    private String owner;

    @Column(name = "path")
    private String path;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version_contract")
    private String versionContract;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "is_zip")
    private Boolean isZip = false;

    @Column(name = "parent_zip_name")
    private String parentZipName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 4. Nueva Entidad de Dominio: `DocumentHistory.java`
**Ruta:** `src/main/java/com/example/fileprocessor/domain/entity/DocumentHistory.java`

```java
package com.example.fileprocessor.domain.entity;

import lombok.Builder;

@Builder
public record DocumentHistory(
    Long id,
    String documentId,
    String productId,
    String useCase,
    String status,
    String errorCode,
    String errorMessage,
    Integer retry,
    LocalDateTime createdAt
) {}
```

### 5. Nueva Entidad R2dbc: `DocumentHistoryEntity.java`
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentHistoryEntity.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "historico_documentos")
public class DocumentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "use_case", nullable = false)
    private String useCase;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry", nullable = false)
    private Integer retry = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

### 6. Nuevo Repository Spring Data: `DocumentRepository.java`
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentRepository.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, Long> {
    Flux<DocumentEntity> findByStatus(String status);
    Mono<DocumentEntity> findByDocumentId(String documentId);
}
```

### 7. Nuevo Repository Spring Data: `DocumentHistoryRepository.java`
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentHistoryRepository.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DocumentHistoryRepository extends R2dbcRepository<DocumentHistoryEntity, Long> {
    Flux<DocumentHistoryEntity> findByDocumentId(String documentId);
    Flux<DocumentHistoryEntity> findByDocumentIdAndUseCase(String documentId, String useCase);
}
```

### 8. Nuevo Puerto de Dominio: `DocumentRepository.java` (out port)
**Ruta:** `src/main/java/com/example/fileprocessor/domain/port/out/DocumentRepository.java`

```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentRepository {
    Mono<Void> save(Document document);
    Mono<Void> updateStatus(String documentId, String status, String errorMessage);
    Mono<Void> updateState(String documentId, String state);
    Flux<Document> findByStatus(String status);
}
```

### 9. Nuevo Puerto de Dominio: `DocumentHistoryRepository.java` (out port)
**Ruta:** `src/main/java/com/example/fileprocessor/domain/port/out/DocumentHistoryRepository.java`

```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentHistoryRepository {
    Mono<Void> save(DocumentHistory history);
    Flux<DocumentHistory> findByDocumentId(String documentId);
    Mono<Integer> getRetryCount(String documentId, String useCase);
}
```

### 10. Nuevos Mappers

**`DocumentMapper.java`**
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/DocumentMapper.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;

import java.time.LocalDateTime;

public class DocumentMapper {

    public static DocumentEntity toEntity(Document domain) {
        return DocumentEntity.builder()
            .documentId(domain.documentId())
            .productId(domain.productId())
            .active(true)
            .docKey(domain.docKey())
            .name(domain.name())
            .owner(domain.owner())
            .path(domain.path())
            .status(domain.status())
            .versionContract(domain.versionContract())
            .state(domain.state())
            .errorMessage(domain.errorMessage())
            .isZip(domain.isZip())
            .parentZipName(domain.parentZipName())
            .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
            .updatedAt(domain.updatedAt() != null ? domain.updatedAt() : LocalDateTime.now())
            .build();
    }

    public static Document toDomain(DocumentEntity entity) {
        return Document.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .productId(entity.getProductId())
            .docKey(entity.getDocKey())
            .name(entity.getName())
            .owner(entity.getOwner())
            .path(entity.getPath())
            .status(entity.getStatus())
            .versionContract(entity.getVersionContract())
            .state(entity.getState())
            .errorMessage(entity.getErrorMessage())
            .isZip(entity.getIsZip())
            .parentZipName(entity.getParentZipName())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
```

**`DocumentHistoryMapper.java`**
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/DocumentHistoryMapper.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;

import java.time.LocalDateTime;

public class DocumentHistoryMapper {

    public static DocumentHistoryEntity toEntity(DocumentHistory domain) {
        return DocumentHistoryEntity.builder()
            .documentId(domain.documentId())
            .productId(domain.productId())
            .useCase(domain.useCase())
            .status(domain.status())
            .errorCode(domain.errorCode())
            .errorMessage(domain.errorMessage())
            .retry(domain.retry())
            .createdAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now())
            .build();
    }

    public static DocumentHistory toDomain(DocumentHistoryEntity entity) {
        return DocumentHistory.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .productId(entity.getProductId())
            .useCase(entity.getUseCase())
            .status(entity.getStatus())
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .retry(entity.getRetry())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
```

### 11. Nuevos Adapters R2dbc

**`DocumentR2dbcAdapter.java`**
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentR2dbcAdapter.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentMapper;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DocumentR2dbcAdapter implements DocumentRepository {

    private final DocumentRepository springDataRepository;

    public DocumentR2dbcAdapter(DocumentRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Mono<Void> save(Document document) {
        return springDataRepository.save(DocumentMapper.toEntity(document)).then();
    }

    @Override
    public Mono<Void> updateStatus(String documentId, String status, String errorMessage) {
        return springDataRepository.findByDocumentId(documentId)
            .flatMap(entity -> {
                entity.setStatus(status);
                entity.setErrorMessage(errorMessage);
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Mono<Void> updateState(String documentId, String state) {
        return springDataRepository.findByDocumentId(documentId)
            .flatMap(entity -> {
                entity.setState(state);
                entity.setUpdatedAt(java.time.LocalDateTime.now());
                return springDataRepository.save(entity);
            })
            .then();
    }

    @Override
    public Flux<Document> findByStatus(String status) {
        return springDataRepository.findByStatus(status)
            .map(DocumentMapper::toDomain);
    }
}
```

**`DocumentHistoryR2dbcAdapter.java`**
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentHistoryR2dbcAdapter.java`

```java
package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper.DocumentHistoryMapper;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DocumentHistoryR2dbcAdapter implements DocumentHistoryRepository {

    private final DocumentHistoryRepository springDataRepository;

    public DocumentHistoryR2dbcAdapter(DocumentHistoryRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Mono<Void> save(DocumentHistory history) {
        return springDataRepository.save(DocumentHistoryMapper.toEntity(history)).then();
    }

    @Override
    public Flux<DocumentHistory> findByDocumentId(String documentId) {
        return springDataRepository.findByDocumentId(documentId)
            .map(DocumentHistoryMapper::toDomain);
    }

    @Override
    public Mono<Integer> getRetryCount(String documentId, String useCase) {
        return springDataRepository.findByDocumentIdAndUseCase(documentId, useCase)
            .collectList()
            .map(list -> list.isEmpty() ? 0 : list.get(list.size() - 1).getRetry());
    }
}
```

### 12. Modificar `ProductDocumentFile.java` — agregar `productId`
**Ruta:** `src/main/java/com/example/fileprocessor/domain/entity/ProductDocumentFile.java`

Agregar campo `String productId`.

### 13. Modificar `ProductRestGatewayAdapter.java`
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java`

Actualizar `getDocument()` para establecer `productId` en el builder:
```java
return ProductDocumentFile.builder()
    .productId(productId)  // AGREGAR ESTO
    .documentId(doc.documentId())
    ...
```

### 14. Nuevo `SyncDocumentsUseCase.java`
**Ruta:** `src/main/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCase.java`

```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Log
@AllArgsConstructor
public class SyncDocumentsUseCase {
    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    public Mono<Void> execute() {
        log.info("Starting document sync");
        return productRestGateway.getAllProducts()
            .flatMap(this::syncDocumentsForProduct)
            .then()
            .doOnTerminate(() -> log.info("Document sync completed"))
            .doOnError(e -> log.severe("Document sync failed: " + e.getMessage()));
    }

    private Mono<Void> syncDocumentsForProduct(ProductHistory product) {
        String productId = product.productId();
        if (product.documents() == null || product.documents().isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(product.documents())
            .flatMap(doc -> productRestGateway.getDocument(productId, doc.documentId()))
            .flatMap(this::processDocument)
            .then();
    }

    private Mono<Void> processDocument(ProductDocumentFile file) {
        ProductDocumentHistory doc = toProductDocument(file);
        if (file.isZip()) {
            return saveDocument(doc, file.productId(), null)
                .thenMany(ZipDecompressor.decompress(doc))
                .flatMap(decompressed ->
                    documentValidator.validate(decompressed)
                        .flatMap(validated -> saveDocument(validated, file.productId(), file.filename())))
                .then();
        } else {
            return documentValidator.validate(doc)
                .flatMap(validated -> saveDocument(validated, file.productId(), null));
        }
    }

    private ProductDocumentHistory toProductDocument(ProductDocumentFile file) {
        return ProductDocumentHistory.builder()
            .documentId(file.documentId())
            .filename(file.filename())
            .content(file.content())
            .contentType(file.contentType())
            .size(file.size())
            .isZip(file.isZip())
            .origin(file.origin())
            .pais(file.pais())
            .build();
    }

    private Mono<Void> saveDocument(ProductDocumentHistory doc, String productId, String parentZipName) {
        Document document = Document.builder()
            .documentId(doc.documentId())
            .productId(productId)
            .name(doc.filename())
            .owner(productId)
            .status(ProductState.PENDING)
            .state(ProductState.SYNCED)
            .isZip(doc.isZip())
            .parentZipName(parentZipName)
            .build();
        return documentRepository.save(document);
    }
}
```

### 15. Modificar `ProductHandler.java`
**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java`

Remover `SyncProductsUseCase`. Solo mantener `SyncDocumentsUseCase`.

### 16. Modificar `AbstractDocumentProcessingUseCase.java`
**Ruta:** `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

- Agregar `DocumentRepository documentRepository` como dependencia
- Agregar `DocumentHistoryRepository historyRepository` como dependencia
- **Remover** `ProductRepository productRepository` (ya no se necesita)
- **Remover** `RulesBussinesGateway documentValidator` (ya no se usa aquí)
- Reemplazar `executePendingDocuments()`:

```java
private static final int MAX_RETRIES = 3;

public Flux<FileUploadResult> executePendingDocuments() {
    return documentRepository.findByStatus(ProductState.PENDING)
        .flatMap(doc -> {
            documentRepository.updateState(doc.documentId(), ProductState.IN_PROGRESS);
            return productRestGateway.getDocument(doc.productId(), doc.documentId())
                .map(file -> toProductDocument(file))
                .flatMap(validated -> uploadDocument(validated, doc.productId()))
                .flatMap(result -> handleUploadSuccess(doc, result))
                .onErrorResume(error -> handleUploadError(doc, error));
        });
}

private Mono<FileUploadResult> handleUploadSuccess(Document doc, FileUploadResult result) {
    DocumentHistory history = DocumentHistory.builder()
        .documentId(doc.documentId())
        .productId(doc.productId())
        .useCase(implementationName())
        .status(DocumentStatus.SUCCESS.name())
        .retry(0)
        .createdAt(LocalDateTime.now())
        .build();
    historyRepository.save(history).subscribe();

    documentRepository.updateStatus(doc.documentId(), ProductState.PROCESSED, null);
    documentRepository.updateState(doc.documentId(), ProductState.PROCESSED);
    return Mono.just(result);
}

private Mono<FileUploadResult> handleUploadError(Document doc, Throwable error) {
    String errorCode = error instanceof ProcessingException pe ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    String errorMsg = error.getMessage();

    return historyRepository.getRetryCount(doc.documentId(), implementationName())
        .defaultIfEmpty(0)
        .flatMap(currentRetry -> {
            DocumentHistory history = DocumentHistory.builder()
                .documentId(doc.documentId())
                .productId(doc.productId())
                .useCase(implementationName())
                .status(DocumentStatus.FAILURE.name())
                .errorCode(errorCode)
                .errorMessage(errorMsg)
                .retry(currentRetry + 1)
                .createdAt(LocalDateTime.now())
                .build();
            historyRepository.save(history).subscribe();

            if (currentRetry + 1 >= MAX_RETRIES) {
                documentRepository.updateStatus(doc.documentId(), ProductState.FAILED, errorMsg);
                documentRepository.updateState(doc.documentId(), ProductState.FAILED);
            } else {
                documentRepository.updateStatus(doc.documentId(), ProductState.PENDING, errorMsg);
            }
            return Mono.just(handleUploadError(error));
        });
}
```

### 17. Modificar `SoapDocumentProcessingUseCase.java` y `S3DocumentProcessingUseCase.java`
- Actualizar para usar `implementationName()` que retorna "SOAP" o "S3" para guardar en historico

### 18. Eliminar archivos relacionados con tablas removidas

| Archivo a Eliminar | Razón |
|--------------------|-------|
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/ProductEntity.java` | Tabla `productos` eliminada |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentHistoryEntity.java` (viejo) | Tabla `historico_documentos` recreada con schema nuevo |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/ProductMapper.java` | Ya no se necesita |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/ProductR2dbcAdapter.java` | Ya no se necesita |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/ProductRepository.java` (Spring Data) | Ya no se necesita |
| `src/main/java/com/example/fileprocessor/domain/entity/ProductHistory.java` | Ya no se necesita |
| `src/main/java/com/example/fileprocessor/domain/entity/DocumentHistory.java` (viejo) | Será reemplazado por nuevo entity con campos adicionales |
| `src/main/java/com/example/fileprocessor/domain/port/out/ProductRepository.java` | Ya no se necesita |
| `src/main/java/com/example/fileprocessor/domain/usecase/SyncProductsUseCase.java` | Funcionalidad absorbida por SyncDocumentsUseCase |

---

## Resumen Flujo de Datos

```
POST /api/v1/products/sync
  → SyncDocumentsUseCase
      → productRestGateway.getAllProducts()
          → por cada producto, por cada documento:
              → productRestGateway.getDocument(productId, documentId)
                  → si isZip=true:
                      → guardar ZIP original (is_zip=true, parent_zip_name=null)
                      → ZipDecompressor.decompress()
                          → por cada archivo descomprimido:
                              → saveDocument(..., parent_zip_name=filename_del_zip)
                  → si isZip=false:
                      → saveDocument(..., parent_zip_name=null)
                  → RulesBussinesGateway.validate()
                      → documentRepository.save()  [status=PENDING, state=SYNCED]

GET /api/v1/products
  → AbstractDocumentProcessingUseCase
      → documentRepository.findByStatus(PENDING)
          → productRestGateway.getDocument()
              → uploadDocument()  [SOAP o S3, SIN decompress/validate]
                  → handleUploadSuccess:
                      → historyRepository.save()  [status=SUCCESS, retry=0, use_case=SOAP/S3]
                      → documentRepository.updateStatus(PROCESSED)
                      → documentRepository.updateState(PROCESSED)
                  → handleUploadError:
                      → historyRepository.save()  [status=FAILURE, retry++, use_case=SOAP/S3]
                      → si retry >= MAX: updateStatus(FAILED), updateState(FAILED)
                      → si retry < MAX: updateStatus(PENDING)
```

---

## Archivos a Crear

| Archivo | Ruta |
|---------|------|
| Document.java | `src/main/java/com/example/fileprocessor/domain/entity/Document.java` |
| DocumentEntity.java | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentEntity.java` |
| DocumentHistory.java (nuevo) | `src/main/java/com/example/fileprocessor/domain/entity/DocumentHistory.java` |
| DocumentHistoryEntity.java (nuevo) | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/entity/DocumentHistoryEntity.java` |
| DocumentRepository.java (Spring Data) | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentRepository.java` |
| DocumentHistoryRepository.java (Spring Data) | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/repository/DocumentHistoryRepository.java` |
| DocumentRepository.java (puerto) | `src/main/java/com/example/fileprocessor/domain/port/out/DocumentRepository.java` |
| DocumentHistoryRepository.java (puerto) | `src/main/java/com/example/fileprocessor/domain/port/out/DocumentHistoryRepository.java` |
| DocumentR2dbcAdapter.java | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentR2dbcAdapter.java` |
| DocumentHistoryR2dbcAdapter.java | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/DocumentHistoryR2dbcAdapter.java` |
| DocumentMapper.java | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/DocumentMapper.java` |
| DocumentHistoryMapper.java | `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/mapper/DocumentHistoryMapper.java` |
| SyncDocumentsUseCase.java | `src/main/java/com/example/fileprocessor/domain/usecase/SyncDocumentsUseCase.java` |

---

## Archivos a Modificar

| Archivo | Cambio |
|---------|--------|
| `src/main/java/com/example/fileprocessor/domain/entity/ProductDocumentFile.java` | Agregar campo `productId` |
| `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java` | Propagar `productId` al builder |
| `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java` | Remover SyncProductsUseCase, usar solo SyncDocumentsUseCase |
| `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java` | Agregar DocumentRepository, DocumentHistoryRepository; cambiar executePendingDocuments(); lógica de reintentos |
| `src/main/java/com/example/fileprocessor/domain/usecase/SoapDocumentProcessingUseCase.java` | Ajustar si usa ProductRepository |
| `src/main/java/com/example/fileprocessor/domain/usecase/S3DocumentProcessingUseCase.java` | Ajustar si usa ProductRepository |

---

## Archivos a Eliminar

| Archivo | Razón |
|---------|-------|
| `ProductEntity.java` | Tabla productos eliminada |
| `ProductMapper.java` | Ya no se necesita |
| `ProductR2dbcAdapter.java` | Ya no se necesita |
| `ProductRepository.java` (Spring Data) | Ya no se necesita |
| `ProductHistory.java` | Ya no se necesita |
| `DocumentHistory.java` (viejo - sin useCase, retry) | Reemplazado por nuevo con campos adicionales |
| `DocumentHistoryEntity.java` (viejo) | Reemplazado por nuevo con campos adicionales |
| `ProductRepository.java` (puerto) | Ya no se necesita |
| `DocumentHistoryRepository.java` (puerto viejo) | Reemplazado por nuevo puerto |
| `DocumentHistoryMapper.java` (viejo) | Reemplazado por nuevo mapper |
| `SyncProductsUseCase.java` | Funcionalidad absorbida |

---

## Verificación

1. Ejecutar migration SQL — verificar que tablas `productos` fue eliminada, `documento` y `historico_documentos` creadas
2. `POST /api/v1/products/sync` — verificar que documentos aparecen en `documento` con status=PENDING, state=SYNCED
3. `GET /api/v1/products?processor=soap` — verificar que:
   - Cada intento se registra en `historico_documentos` con `use_case`=SOAP, `retry` incrementado
   - Éxito: status=PROCESSED en `documento`
   - Error + retry<3: status=PENDING en `documento`
   - Error + retry>=3: status=FAILED en `documento`
4. Consultar `historico_documentos` para ver trazabilidad completa por documento y caso de uso
5. Documentos ZIP deben ser descomprimidos y validados durante sync, pero NO durante procesamiento
6. Ejecutar tests existentes — ajustar según sea necesario por los cambios