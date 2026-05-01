# Plan de Implementacion: Descompresion de archivos ZIP

---

## 1. Objetivo

Agregar soporte para descomprimir archivos ZIP antes de la validacion y envio de documentos. Cuando un `ProductDocument` tiene `isZip=true`, el contenido ZIP debe expandirse en archivos individuales que luego se procesan normalmente.

---

## 2. Comportamiento esperado

| Escenario | Resultado |
|----------|-----------|
| Documento normal (isZip=false) | Se procesa tal cual |
| Documento ZIP con 3 archivos | Se expande en 3 `ProductDocument` individuales |
| ZIP vacio | Se loguea warning, no produce documentos |
| ZIP corrupto | `ProcessingException` con errorCode `INVALID_ZIP` |
| Archivo dentro de ZIP no pasa validacion | Se filtra igual que documento normal |

---

## 3. Flujo modificado

### Antes (pipeline actual)
```
getDocument()
    .flatMap(documentValidator::validate)
    .flatMap(uploadDocument())
```

### Despues (nuevo pipeline)
```
getDocument()
    .flatMap(decompressIfNeeded())     ← NUEVO: expande ZIP en archivos individuales
    .flatMap(documentValidator::validate)
    .flatMap(uploadDocument())
```

---

## 4. Punto de inyeccion

**Archivo:** `AbstractDocumentProcessingUseCase.java`
**Ubicacion:** Linea 35, dentro del `flatMap` que procesa cada documento

```java
.flatMap(doc -> productRestGateway.getDocument(product.productId(), doc.documentId())
    .flatMap(this::decompressIfNeeded)   // ← INSERTAR AQUI
    .flatMap(documentValidator::validate)
    .flatMap(validated -> uploadDocument(validated, product.productId())))
```

---

## 5. Archivos a crear

### `src/main/java/com/example/fileprocessor/domain/util/ZipDecompressor.java`

```java
package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<ProductDocument> decompress(ProductDocument zipDoc) {
        if (!zipDoc.isZip()) {
            return Flux.just(zipDoc);
        }
        return Flux.fromIterable(readZipEntries(zipDoc))
            .filter(entry -> entry.filename() != null && !entry.filename().isBlank())
            .map(entry -> buildProductDocument(entry, zipDoc));
    }

    private static Iterable<ZipEntryInfo> readZipEntries(ProductDocument zipDoc) {
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipDoc.content()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] decompressed = zis.readAllBytes();
                    yield new ZipEntryInfo(entry.getName(), decompressed);
                }
            }
        } catch (IOException e) {
            throw ProcessingException.withTraceId(
                "Failed to decompress ZIP: " + zipDoc.documentId(),
                ProcessingResultCodes.INVALID_ZIP, zipDoc.documentId());
        }
        return List.of();
    }

    private static ProductDocument buildProductDocument(ZipEntryInfo info, ProductDocument original) {
        return ProductDocument.builder()
            .documentId(original.documentId() + "/" + info.filename())
            .filename(info.filename())
            .content(info.content())
            .contentType(inferContentType(info.filename()))
            .size(info.content().length)
            .isZip(false)
            .origin(original.origin())
            .build();
    }

    private static String inferContentType(String filename) {
        if (filename.endsWith(".pdf")) return "application/pdf";
        if (filename.endsWith(".csv")) return "text/csv";
        if (filename.endsWith(".txt")) return "text/plain";
        if (filename.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (filename.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private record ZipEntryInfo(String filename, byte[] content) {}
}
```

**Nota:** `yield` es una keyword de Java 21 (switch patterns). Alternativamente se puede usar `List.of()` dentro del while loop.

---

## 6. Codigo de error nuevo

**Archivo:** `ProcessingResultCodes.java`

```java
public static final String INVALID_ZIP = "INVALID_ZIP";
```

---

## 7. Tests a crear

### `src/test/java/com/example/fileprocessor/domain/util/ZipDecompressorTest.java`

| Test | Descripcion |
|------|-------------|
| `decompress_nonZip_returnsSameDocument` | isZip=false retorna el mismo documento |
| `decompress_zipWithTwoFiles_expandsToTwoDocuments` | ZIP con 2 archivos → 2 ProductDocument |
| `decompress_emptyZip_returnsEmptyFlux` | ZIP sin entradas → Flux.empty() |
| `decompress_corruptZip_throwsException` | ZIP corrupto → ProcessingException |

### Test en UseCase existente

Agregar test en `SoapDocumentProcessingUseCaseTest`:

```java
@Test
void executePendingDocuments_withZipDocument_expandsAndProcesses() {
    // ZIP con test.pdf dentro
    byte[] zipContent = createTestZip("test.pdf", new byte[]{1});
    ProductDocument zipDoc = new ProductDocument(
        "doc-1", "documents.zip", zipContent, "application/zip", zipContent.length, true, "origin");
    Product product = new Product("prod-1", "Test", LocalDateTime.now(), "PENDING", null, List.of(zipDoc));

    when(productDbGateway.findByLoadDate(any())).thenReturn(Flux.just(product));
    when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(zipDoc));

    StepVerifier.create(useCase.executePendingDocuments())
        .expectNextCount(1)  // 1 archivo dentro del ZIP
        .verifyComplete();
}
```

---

## 8. Impacto en pipeline

- Los archivos descomprimidos herendan `origin` del documento ZIP original
- Cada archivo pasa por las mismas validaciones (tamano maximo, patron filename)
- El `documentId` del archivo descomprimido incluye el path original: `doc-1/test.pdf`
- No hay cambio en el comportamiento de `RulesBussinesService`

---

## 9. Verificacion final

```bash
./gradlew test
./gradlew build
```

Todos los tests pasan y el build compila sin errores.

---

## 10. Archivos involucrados

| Accion | Archivo |
|--------|---------|
| Crear | `src/main/java/com/example/fileprocessor/domain/util/ZipDecompressor.java` |
| Modificar | `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java` |
| Modificar | `src/main/java/com/example/fileprocessor/domain/usecase/ProcessingResultCodes.java` |
| Crear | `src/test/java/com/example/fileprocessor/domain/util/ZipDecompressorTest.java` |
| Modificar | `src/test/java/com/example/fileprocessor/domain/usecase/SoapDocumentProcessingUseCaseTest.java` |
