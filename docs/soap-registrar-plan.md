# Plan de Implementación: Nuevo Caso de Uso SOAP (Registrar con Metadata Objeto y APIs Alternativas)

Este documento detalla el alcance completo de los cambios para la implementación del nuevo caso de uso SOAP ("soap-registrar"). Incluye la creación de nuevos componentes, las modificaciones necesarias y el código fuente correspondiente para cada archivo.

---

## 1. Alcance General (Scope)

El objetivo es implementar un nuevo procesador SOAP (`soap-registrar`) que:
1. Reutilice la estructura de plantilla SOAP existente (`soap-envelope.xml`) con el tag `<v1:transmitirDocumentoRequest>`, pero **personalice dinámicamente el contenido de `<metaData> </metaData>`** mediante un objeto Java de metadata fija (`RegistrarMetadata`).
2. Consuma **APIs REST diferentes** para descargar el archivo y sus propiedades (en lugar de la API de productos estándar), haciendo que el método plantilla `downloadDocument` sea sobrescribible.
3. Se exponga a través de la ruta REST reactiva existente bajo el identificador de tipo de trabajo `"soap-registrar"`.

---

## 2. Archivos Nuevos (Creaciones)

### 2.1. Modelo del Dominio: `RegistrarMetadata.java`
**Ruta**: [RegistrarMetadata.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/entity/RegistrarMetadata.java)
```java
package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegistrarMetadata {
    private final String fecha;
    private final String comentario;
    private final String producto;
    private final String pais;
}
```

### 2.2. Puerto del Dominio (Gateway Interface): `RegistrarRestGateway.java`
**Ruta**: [RegistrarRestGateway.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/port/out/RegistrarRestGateway.java)
```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import reactor.core.publisher.Mono;

public interface RegistrarRestGateway {
    Mono<ProductDocumentFile> getRegistrarDocument(String documentId);
}
```

### 2.3. Caso de Uso del Dominio: `SoapRegistrarDocumentProcessingUseCase.java`
**Ruta**: [SoapRegistrarDocumentProcessingUseCase.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/usecase/SoapRegistrarDocumentProcessingUseCase.java)
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.RegistrarMetadata;
import com.example.fileprocessor.domain.port.out.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.logging.Level;

public class SoapRegistrarDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;
    private final RegistrarRestGateway registrarRestGateway;

    public SoapRegistrarDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            RulesBussinesGateway documentValidator,
            HomologationRepository homologationRepository,
            RegistrarRestGateway registrarRestGateway,
            String tempDirPath) {
        super(persistencePort, productRestGateway, documentValidator, tempDirPath);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
        this.registrarRestGateway = registrarRestGateway;
    }

    @Override
    protected Mono<DocumentHistoryDTO> downloadDocument(DocumentHistoryDTO baseHistory) {
        // Consumir API alternativa para la descarga del archivo
        return registrarRestGateway.getRegistrarDocument(baseHistory.getBusinessDocumentId())
                .map(file -> baseHistory.toBuilder()
                        .content(file.getContent())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .filename(file.getFilename())
                        .originFolder(file.getOriginFolder())
                        .originCountry(file.getOriginCountry())
                        .isZip(file.getIsZip())
                        .build());
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
        return homologationRepository.resolve(history)
                .flatMapMany(h -> {
                    FileUploadRequest request = FileUploadRequest.from(history, docId, h);
                    
                    // Asignar el objeto de metadatos fijos específico para registro
                    request.setRegistrarMetadata(RegistrarMetadata.builder()
                            .fecha(java.time.LocalDate.now().toString())
                            .comentario("Registro de documento automatico")
                            .producto(history.getProductId())
                            .pais(h.homologationCountry() != null ? h.homologationCountry().homologationCountry() : history.getOriginCountry())
                            .build());

                    return soapGateway.send(request)
                            .map(resp -> resp.toBuilder()
                                    .homologationFolder(h.homologationCountry() != null ? h.homologationCountry().homologationFolder() : null)
                                    .homologationCountry(h.homologationCountry() != null ? h.homologationCountry().homologationCountry() : null)
                                    .categoriaHomologada(h.categoriaDocument())
                                    .build());
                })
                .onErrorResume(e -> {
                    LOGGER.log(Level.SEVERE, "SOAP Registrar fatal failure for docId {0}: {1}",
                            new Object[] { docId, e.getMessage() });

                    return Flux.just(FileUploadResponse.builder()
                            .status(ProcessingResultCodes.FAILURE.name())
                            .syncStatus(ProcessingResultCodes.UNKNOWN_ERROR.name())
                            .message(e.getMessage())
                            .success(false)
                            .filename(history.getFilename())
                            .processedAt(Instant.now())
                            .build());
                });
    }

    @Override
    protected String implementationName() {
        return "SOAP_REGISTRAR";
    }
}
```

### 2.4. Adaptador de Infraestructura REST: `RegistrarRestGatewayAdapter.java`
**Ruta**: [RegistrarRestGatewayAdapter.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/RegistrarRestGatewayAdapter.java)
```java
package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.port.out.RegistrarRestGateway;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RegistrarRestGatewayAdapter implements RegistrarRestGateway {
    
    @Override
    public Mono<ProductDocumentFile> getRegistrarDocument(String documentId) {
        // Implementación de simulación/mock. En producción aquí se realizaría la llamada WebClient a la otra API.
        return Mono.just(ProductDocumentFile.builder()
                .documentId(documentId)
                .filename("registrar_test.pdf")
                .content("Mock content from alternate API".getBytes())
                .contentType("application/pdf")
                .size(31L)
                .isZip(false)
                .originFolder("garantia")
                .originCountry("co")
                .build());
    }
}
```

### 2.5. Pruebas Unitarias del Caso de Uso: `SoapRegistrarDocumentProcessingUseCaseTest.java`
**Ruta**: [SoapRegistrarDocumentProcessingUseCaseTest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/test/java/com/example/fileprocessor/domain/usecase/SoapRegistrarDocumentProcessingUseCaseTest.java)
```java
package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapRegistrarDocumentProcessingUseCaseTest {

    @Mock private DocumentPersistenceGateway persistencePort;
    @Mock private ProductRestGateway productRestGateway;
    @Mock private SoapGateway soapGateway;
    @Mock private RulesBussinesGateway documentValidator;
    @Mock private HomologationRepository homologationRepository;
    @Mock private RegistrarRestGateway registrarRestGateway;

    private SoapRegistrarDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SoapRegistrarDocumentProcessingUseCase(
                persistencePort, productRestGateway, soapGateway, documentValidator,
                homologationRepository, registrarRestGateway, "/tmp"
        );
    }

    @Test
    void executePendingDocuments_delegatesToAlternativeDownloadAndUploadsWithRegistrarMetadata() {
        Document pending = Document.builder()
                .id(1L)
                .documentId("doc-registrar-1")
                .productId("prod-registrar-1")
                .retryCount(0)
                .isZip(false)
                .build();

        ProductDocumentFile documentFile = ProductDocumentFile.builder()
                .documentId("doc-registrar-1")
                .filename("test-registrar.pdf")
                .content("hello content".getBytes())
                .size(13L)
                .contentType("application/pdf")
                .isZip(false)
                .originFolder("folder")
                .originCountry("country")
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("SOAP_REGISTRAR"), any(LocalDateTime.class)))
                .thenReturn(Flux.just(pending));
        when(persistencePort.lockDocumentForProcessing(eq(1L), eq(0)))
                .thenReturn(Mono.just(1L));
        when(registrarRestGateway.getRegistrarDocument("doc-registrar-1"))
                .thenReturn(Mono.just(documentFile));
        when(documentValidator.validate(any(), anyBoolean()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(homologationRepository.resolve(any()))
                .thenReturn(Mono.just(HomologationResult.builder().categoriaDocument("Manual").build()));
        when(soapGateway.send(any(FileUploadRequest.class)))
                .thenReturn(Flux.just(FileUploadResponse.builder().success(true).status("SUCCESS").build()));
        when(persistencePort.finalizeProcessingAtomically(any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
                .expectNextMatches(FileUploadResponse::isSuccess)
                .verifyComplete();

        verify(registrarRestGateway, times(1)).getRegistrarDocument("doc-registrar-1");
        verify(soapGateway, times(1)).send(argThat(request -> 
            request.getRegistrarMetadata() != null &&
            "Registro de documento automatico".equals(request.getRegistrarMetadata().getComentario())
        ));
    }
}
```

---

## 3. Archivos Modificados (Modificaciones)

### 3.1. [AbstractDocumentProcessingUseCase.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java)
Cambiar la visibilidad del método plantilla de descarga a `protected`.

```diff
-    private Mono<DocumentHistoryDTO> downloadDocument(DocumentHistoryDTO baseHistory) {
+    protected Mono<DocumentHistoryDTO> downloadDocument(DocumentHistoryDTO baseHistory) {
```

### 3.2. [FileUploadRequest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/domain/entity/FileUploadRequest.java)
Añadir propiedad `registrarMetadata`.

```diff
     private String homologationFolder;
     private String homologationCountry;
     private Long docId;
+    private RegistrarMetadata registrarMetadata;
```

### 3.3. [soap-envelope.xml](file:///Users/alexander2305/Downloads/file-processor-service/src/main/resources/templates/soap-envelope.xml)
Reemplazar el cuerpo fijo por `{{metaDataContent}}`.

```diff
             <carpetaHomologada>{{carpetaHomologada}}</carpetaHomologada>
             <metaData>
-                <tiposMetaData>
-                    <nombre>{{metaNameFecha}}</nombre>
-                    <valor>{{fecha}}</valor>
-                </tiposMetaData>
-                <tiposMetaData>
-                    <nombre>{{metaNameComentario}}</nombre>
-                    <valor>{{comentario}}</valor>
-                </tiposMetaData>
+{{metaDataContent}}
             </metaData>
```

### 3.4. [SoapMapper.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java)
Cambiar `buildEnvelope` y agregar función auxiliar `appendMetaTag`.

```diff
             String catHom = request.getCategoriaDocument() != null ? request.getCategoriaDocument() : "";
             String paisHom = request.getHomologationCountry() != null ? request.getHomologationCountry() : "";
             String carpHom = request.getHomologationFolder() != null ? request.getHomologationFolder() : "";
 
-            String fecha = java.time.LocalDate.now().toString();
-            String comentario = SoapConstants.VAL_DEFAULT_COMENTARIO;
-
-            return this.xmlTemplate
-                    .replace(SoapConstants.T_TRACE_ID, escapeXml(traceId))
-                    .replace(SoapConstants.T_TIMESTAMP, Instant.now().toString())
-                    .replace(SoapConstants.T_SUBTYPE, safeSubtype)
-                    .replace(SoapConstants.T_FILENAME, safeFilename)
-                    .replace(SoapConstants.T_CAT_HOM, escapeXml(catHom))
-                    .replace(SoapConstants.T_PAIS_HOM, escapeXml(paisHom))
-                    .replace(SoapConstants.T_CARP_HOM, escapeXml(carpHom))
-                    .replace(SoapConstants.T_META_NAME_FECHA, SoapConstants.VAL_META_NAME_FECHA)
-                    .replace(SoapConstants.T_FECHA, escapeXml(fecha))
-                    .replace(SoapConstants.T_META_NAME_COMENTARIO, SoapConstants.VAL_META_NAME_COMENTARIO)
-                    .replace(SoapConstants.T_COMENTARIO, escapeXml(comentario))
-                    .replace(SoapConstants.T_CONTENT, base64Content);
+            StringBuilder metaBuilder = new StringBuilder();
+            if (request.getRegistrarMetadata() != null) {
+                RegistrarMetadata rm = request.getRegistrarMetadata();
+                appendMetaTag(metaBuilder, "Rfecha", rm.getFecha());
+                appendMetaTag(metaBuilder, "Rcomentario", rm.getComentario());
+                appendMetaTag(metaBuilder, "Rproducto", rm.getProducto());
+                appendMetaTag(metaBuilder, "Rpais", rm.getPais());
+            } else {
+                String fecha = java.time.LocalDate.now().toString();
+                String comentario = SoapConstants.VAL_DEFAULT_COMENTARIO;
+                appendMetaTag(metaBuilder, SoapConstants.VAL_META_NAME_FECHA, fecha);
+                appendMetaTag(metaBuilder, SoapConstants.VAL_META_NAME_COMENTARIO, comentario);
+            }
+            String metaDataXml = metaBuilder.toString().stripTrailing();
+
+            return this.xmlTemplate
+                    .replace(SoapConstants.T_TRACE_ID, escapeXml(traceId))
+                    .replace(SoapConstants.T_TIMESTAMP, Instant.now().toString())
+                    .replace(SoapConstants.T_SUBTYPE, safeSubtype)
+                    .replace(SoapConstants.T_FILENAME, safeFilename)
+                    .replace(SoapConstants.T_CAT_HOM, escapeXml(catHom))
+                    .replace(SoapConstants.T_PAIS_HOM, escapeXml(paisHom))
+                    .replace(SoapConstants.T_CARP_HOM, escapeXml(carpHom))
+                    .replace("{{metaDataContent}}", metaDataXml)
+                    .replace(SoapConstants.T_CONTENT, base64Content);
```

Agregar método auxiliar al final de [SoapMapper.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java):
```java
    private void appendMetaTag(StringBuilder sb, String name, String value) {
        sb.append("                <tiposMetaData>\n")
          .append("                    <nombre>").append(escapeXml(name)).append("</nombre>\n")
          .append("                    <valor>").append(escapeXml(value)).append("</valor>\n")
          .append("                </tiposMetaData>\n");
    }
```

### 3.5. [ApiConstants.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java)
Añadir el nuevo tipo de procesador.

```diff
     // Processor types
     public static final String PROCESSOR_SOAP = "soap";
+    public static final String PROCESSOR_SOAP_REGISTRAR = "soap-registrar";
     public static final String PROCESSOR_S3 = "s3";
```

### 3.6. [DomainConfig.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/application/service/config/DomainConfig.java)
Registrar el nuevo bean `SoapRegistrarDocumentProcessingUseCase`.

```diff
+    @Bean
+    @ConditionalOnBean(SoapGateway.class)
+    public SoapRegistrarDocumentProcessingUseCase soapRegistrarDocumentUseCase(
+            DocumentPersistenceGateway persistencePort,
+            ProductRestGateway productRestGateway,
+            SoapGateway soapGateway,
+            HomologationRepository homologationRepository,
+            RegistrarRestGateway registrarRestGateway,
+            ProcessorsProperties properties) {
+        return new SoapRegistrarDocumentProcessingUseCase(
+            persistencePort,
+            productRestGateway,
+            soapGateway,
+            new RulesBussinesService(properties.soap()),
+            homologationRepository,
+            registrarRestGateway,
+            properties.zipTempDir()
+        );
+    }
```

### 3.7. [ProductHandler.java](file:///Users/alexander2305/Downloads/file-processor-service/src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java)
Inyectar y mapear el caso de uso.

```diff
     private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
+    private final AbstractDocumentProcessingUseCase soapRegistrarDocumentUseCase;
     private final ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
```
```diff
     public ProductHandler(
             SoapDocumentProcessingUseCase soapDocumentUseCase,
+            SoapRegistrarDocumentProcessingUseCase soapRegistrarDocumentUseCase,
             ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider,
```
```diff
         this.soapDocumentUseCase = soapDocumentUseCase;
+        this.soapRegistrarDocumentUseCase = soapRegistrarDocumentUseCase;
         this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
```
```diff
     AbstractDocumentProcessingUseCase getProcessor(String processorType) {
         return switch (processorType) {
             case ApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
+            case ApiConstants.PROCESSOR_SOAP_REGISTRAR -> soapRegistrarDocumentUseCase;
             case ApiConstants.PROCESSOR_S3 -> {
```

### 3.8. [ProductHandlerTest.java](file:///Users/alexander2305/Downloads/file-processor-service/src/test/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandlerTest.java)
Mapear el mock del nuevo caso de uso e inyectarlo en el setUp.

```java
<<<<
    @Mock
    private SoapDocumentProcessingUseCase soapDocumentUseCase;
    @Mock
    private ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
====
    @Mock
    private SoapDocumentProcessingUseCase soapDocumentUseCase;
    @Mock
    private SoapRegistrarDocumentProcessingUseCase soapRegistrarDocumentUseCase;
    @Mock
    private ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
>>>>
```
```java
<<<<
        handler = new ProductHandler(soapDocumentUseCase, s3DocumentUseCaseProvider, syncDocumentsUseCase, getSyncStatusUseCase, getProcessStatusUseCase);
====
        handler = new ProductHandler(soapDocumentUseCase, soapRegistrarDocumentUseCase, s3DocumentUseCaseProvider, syncDocumentsUseCase, getSyncStatusUseCase, getProcessStatusUseCase);
>>>>
```
```java
    @Test
    @DisplayName("Debe retornar el caso de uso SOAP registrar si se solicita soap-registrar")
    void getProcessor_whenSoapRegistrar_returnsSoapRegistrarUseCase() {
        assertSame(soapRegistrarDocumentUseCase, handler.getProcessor("soap-registrar"));
    }
```
*(Nota: Se agregará este caso de prueba en ProductHandlerTest.java)*

---

## 4. Estrategia de Validación y Pruebas
1. Ejecutar pruebas unitarias locales mediante `./gradlew test`.
2. Validar que la cobertura de mutación y pruebas no se degrade.
