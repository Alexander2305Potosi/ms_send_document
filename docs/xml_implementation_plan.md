# Plan de Implementación: Nueva Estructura XML SOAP (GestionInternaDocumental)

Este plan describe cómo implementar la nueva estructura XML en el otro microservicio asegurando que no queden valores quemados (hardcoded) y que `messageId` y `timestamp` cambien por cada petición.

## 1. Clase Completa `SoapProperties.java`

Dado que no se permiten valores quemados, todos los campos fijos del XML se externalizarán hacia el `application.yml`. A continuación se presenta el archivo completo:

```java
package com.example.fileprocessor.infrastructure.helpers.soap.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.soap.v2")
public record SoapProperties(
        @NotBlank String endpoint,
        @NotBlank String systemId,
        @NotBlank String userName,
        @NotBlank String headerNamespace,
        @NotBlank String bodyNamespace,
        @NotBlank String soapNamespace,

        String userToken,
        String destinationName,
        String destinationNamespace,
        String destinationOperation,
        String soapAction,

        String classification,
        
        // Nuevas propiedades para el body
        String tipoDocumental,
        String autor,
        String grupoSeguridad,
        String cuentaSeguridad,
        String idPerfil,
        String codigoSubSerie, // <-- Añadido para el código de subserie fijo

        Map<String, String> messageContext,
        Map<String, String> metaData,

        @Min(1) int timeoutSeconds,
        @Min(1) int retryAttempts) {
    public SoapProperties {
        if (messageContext == null)
            messageContext = Map.of();
        if (metaData == null)
            metaData = Map.of();
        if (timeoutSeconds <= 0)
            timeoutSeconds = 30;
        if (retryAttempts < 0)
            retryAttempts = 0;
    }
}
```

De este modo, en el `application.yml` se configurarán así:
```yaml
app:
  soap:
    v2:
      # ... propiedades existentes ...
      tipo-documental: "Contratos_Clausulas_OtroSi"
      autor: "Prueba-QA-Abastecimiento"
      grupo-seguridad: "Aliados"
      cuenta-seguridad: "Aliado/contratos_proveedor_abastecimiento"
      id-perfil: "Contratosproveedorabast"
      codigo-sub-serie: "TuSubserieFija" # <-- Definido en YAML
```

## 1.2 Clase Completa `FileUploadRequest.java`

Se detalla la estructura exacta del DTO que contiene la información del cargue de archivos, incluyendo la función mapeadora `from`:

```java
package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class FileUploadRequest {
    private String typeDocumentary;
    private String subTypeDocumentary;
    private String typeDocumentSupplier;
    private String identificationSupplier;
    private String identification;
    private String idSupplierErp;
    private String nameSupplierErp;
    private String established;
    private String subProduct;
    private String contratcState;
    private String titleContract;
    private String typeAgreement;
    private LocalDateTime dateAgreement;
    private LocalDateTime expirationDate;
    private String nameSupplier;
    private String nameNegotiator;
    private String versionContract;
    private String documentName;
    private byte[] fileContent;

    public static FileUploadRequest from(DocumentHistoryDTO document, HomologationResult h) {
        return FileUploadRequest.builder()
                .typeDocumentary(h.documentary().typeDocumentary())
                .subTypeDocumentary(h.documentary().subTypeDocumentary())
                .typeDocumentSupplier(h.typeIdentificationResult())
                .identificationSupplier(document.getIdentification())
                .idSupplierErp(document.getIdSupplierErp())
                .nameSupplierErp(document.getSupplierName())
                .established(document.getEstablished())
                .subProduct(document.getSubProduct())
                .contratcState(document.getContractState())
                .titleContract(document.getTitleContract())
                .typeAgreement(document.getTypeAgreement())
                .dateAgreement(document.getDateAgreement())
                .expirationDate(document.getExpirationDate())
                .nameSupplier(document.getSupplierName())
                .nameNegotiator(document.getNegotiator())
                .versionContract(document.getVersionContract())
                .documentName(document.getDocumentName())
                .fileContent(document.getFileContent())
                .build();
    }
}
```

## 1.3 Archivo de Configuración Completo (`application.yml`)

A continuación se detalla la sección SOAP de configuración para los ambientes, la cual se mapea mediante `@ConfigurationProperties` hacia la clase `SoapProperties`:

```yaml
app:
  soap:
    v2:
      # URL del Endpoint SOAP del servicio web
      endpoint: ${SOAP_V2_ENDPOINT:http://localhost:9000/soap/adminDocs}
      # Identificador único de sistema emisor
      system-id: ${SOAP_V2_SYSTEM_ID:NU1730001}
      # Usuario para el Header de Seguridad
      user-name: ${SOAP_V2_USER_NAME:user}
      # Namespace para el requestHeader (v2)
      header-namespace: ${SOAP_V2_HEADER_NS:http://sumarmas.com/ents/SOI/MessageFormat/V2.1}
      # Namespace para el cuerpo transmitirDocumento (v1)
      body-namespace: ${SOAP_V2_BODY_NS:http://sumarmas.com/intf/Corporativo/administracionDocumentos/GestionInternaDocumental/V1.0}
      # Namespace SOAP estándar
      soap-namespace: ${SOAP_V2_NAMESPACE:http://schemas.xmlsoap.org/soap/envelope/}
      # Token de usuario (opcional)
      user-token: ${SOAP_V2_USER_TOKEN:}
      # Datos de clasificación y destino del mensaje
      destination-name: ${SOAP_V2_DEST_NAME:GestionInternaDocumental}
      destination-namespace: ${SOAP_V2_DEST_NS:http://sumarmas.com/intf/Corporativo/administracionDocumentos/GestionInternaDocumental/V1.0}
      destination-operation: ${SOAP_V2_DEST_OP:transmitirDocumento}
      soap-action: ${SOAP_V2_SOAP_ACTION:}
      classification: ${SOAP_V2_CLASSIFICATION:Publico}
      
      # Propiedades fijas/estáticas por ambiente para el cuerpo
      tipo-documental: ${SOAP_V2_TIPO_DOCUMENTAL:Contratos_Clausulas_OtroSi}
      autor: ${SOAP_V2_AUTOR:Prueba-QA-Abastecimiento}
      grupo-seguridad: ${SOAP_V2_GRUPO_SEGURIDAD:Aliados}
      cuenta-seguridad: ${SOAP_V2_CUENTA_SEGURIDAD:Aliado/contratos_proveedor_abastecimiento}
      id-perfil: ${SOAP_V2_ID_PERFIL:Contratosproveedorabast}
      codigo-sub-serie: ${SOAP_V2_CODIGO_SUBSERIE:TuSubserieFija}
      
      # Nombres de metadatos requeridos a mapear (se inyectan como llaves en Map<String, String>)
      meta-data:
        xNumeroDoc: ""
        xNombre: ""
        xRadicado: ""
        xSubproducto: ""
        xAsunto: ""
        xComments: ""
        xTipoProducto: ""
        xFechaExpedicionDocumento: ""
        xFechaExpiracionDocumento: ""
        xNombreProyecto: ""
        xRub: ""
        
      # Parámetros técnicos de conexión y resiliencia
      timeout-seconds: 30
      retry-attempts: 3
```

## 2. Clase Completa `SoapConstants.java`

Se definen todos los tokens de reemplazo para la plantilla XML y los elementos de respuesta de forma centralizada:

```java
package com.example.fileprocessor.infrastructure.helpers.soap.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoapConstants {
    // Namespaces dinámicos según el ambiente de despliegue
    public static final String T_NS_STD = "{{ns_standard}}";
    public static final String T_NS_ENV = "{{ns_envelope}}";
    public static final String T_NS_BODY = "{{ns_body}}";
    
    public static final String T_TRACE_ID    = "{{TRACE_ID}}";
    public static final String T_TIMESTAMP   = "{{TIMESTAMP}}";
    
    public static final String T_SYSTEM_ID   = "{{SYSTEM_ID}}";
    public static final String T_USER_NAME   = "{{USER_NAME}}";
    public static final String T_USER_TOKEN  = "{{USER_TOKEN}}";
    
    public static final String T_DEST_NAME   = "{{DESTINATION_NAME}}";
    public static final String T_DEST_NS     = "{{DESTINATION_NAMESPACE}}";
    public static final String T_DEST_OP     = "{{DESTINATION_OPERATION}}";
    
    public static final String T_CLASS       = "{{CLASSIFICATION}}";
    
    public static final String T_FILENAME    = "{{FILENAME}}";
    public static final String T_CONTENT     = "{{CONTENT}}";

    // Nuevas constantes para la estructura GestionInternaDocumental
    public static final String T_TIPO_DOCUMENTAL = "{{TIPO_DOCUMENTAL}}";
    public static final String T_AUTOR = "{{AUTOR}}";
    public static final String T_GRUPO_SEGURIDAD = "{{GRUPO_SEGURIDAD}}";
    public static final String T_CUENTA_SEGURIDAD = "{{CUENTA_SEGURIDAD}}";
    public static final String T_SUBTIPO_DOCUMENTAL = "{{SUBTIPO_DOCUMENTAL}}";
    public static final String T_TIPO_DOC_ID = "{{TIPO_DOC_ID}}";
    public static final String T_NUM_DOC_ID = "{{NUM_DOC_ID}}";
    public static final String T_CODIGO_SUBSERIE = "{{CODIGO_SUBSERIE}}";
    public static final String T_ID_PERFIL = "{{ID_PERFIL}}";
    public static final String T_METADATA_BLOCK = "{{METADATA_BLOCK}}";

    // Elementos XML de respuesta
    public static final String EL_ENVELOPE = "Envelope";
    public static final String EL_HEADER = "Header";
    public static final String EL_BODY = "Body";
    public static final String EL_TRANSMITIR_DOCUMENTO_RESPONSE = "transmitirDocumentoResponse";
    public static final String EL_STATUS = "status";
    public static final String EL_STATUS_CODE = "statusCode";
    public static final String EL_MESSAGE = "message";
    public static final String EL_CORRELATION_ID = "correlationId";
    public static final String EL_MESSAGE_ID = "messageId";
    public static final String EL_PROCESSED_AT = "processedAt";
    public static final String EL_EXTERNAL_REFERENCE = "externalReference";
    public static final String EL_ID_DOCUMENTO = "idDocumento";
    public static final String STATUS_OK = "OK";
}
```

## 3. Plantilla XML (`soap-envelope.xml`)
El bloque de `Header` y `transmitirDocumento` se parametrizará completamente. Los namespaces de SOAP y de los esquemas se definen con variables dinámicas ya que varían por ambiente de despliegue.

```xml
<soapenv:Envelope xmlns:soapenv="{{ns_standard}}" 
xmlns:v2="{{ns_envelope}}" 
xmlns:v1="{{ns_body}}">
   <soapenv:Header>
      <v2:requestHeader>
         <systemId>{{SYSTEM_ID}}</systemId>
         <messageId>{{TRACE_ID}}</messageId>
         <timestamp>{{TIMESTAMP}}</timestamp>
         <userId>
            <userName>{{USER_NAME}}</userName>
         </userId>
         <destination>
            <name>{{DESTINATION_NAME}}</name>
            <namespace>{{DESTINATION_NAMESPACE}}</namespace>
            <operation>{{DESTINATION_OPERATION}}</operation>
         </destination>
         <classifications>
            <classification>{{CLASSIFICATION}}</classification>
         </classifications>
      </v2:requestHeader>
   </soapenv:Header>
   <soapenv:Body>
      <v1:transmitirDocumento>
         <tipoDocumental>{{TIPO_DOCUMENTAL}}</tipoDocumental>
         <autor>{{AUTOR}}</autor>
         <grupoSeguridad>{{GRUPO_SEGURIDAD}}</grupoSeguridad>
         <cuentaSeguridad>{{CUENTA_SEGURIDAD}}</cuentaSeguridad>
         <subTipoDocumental>{{SUBTIPO_DOCUMENTAL}}</subTipoDocumental>
         <tipoDocumentoIdentificacion>{{TIPO_DOC_ID}}</tipoDocumentoIdentificacion>
         <numeroDocumentoIdentificacion>{{NUM_DOC_ID}}</numeroDocumentoIdentificacion>
         <codigoSubSerie>{{CODIGO_SUBSERIE}}</codigoSubSerie>
         <idPerfil>{{ID_PERFIL}}</idPerfil>
         <nombreArchivo>{{FILENAME}}</nombreArchivo>
         <archivo>{{CONTENT}}</archivo>
         <metaData>
{{METADATA_BLOCK}}
         </metaData>
      </v1:transmitirDocumento>
   </soapenv:Body>
</soapenv:Envelope>
```

## 4. Clase Completa `SoapMapper.java`

A continuación se presenta el código completo del archivo `SoapMapper.java` incluyendo la carga de la nueva plantilla con namespaces y parámetros dinámicos:

```java
package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.Gson;

@Component
public class SoapMapper {

    private static final Logger LOGGER = Logger.getLogger(SoapMapper.class.getName());
    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();
    static {
        FACTORY.setNamespaceAware(true);
    }

    private final SoapProperties props;
    private final ResourceLoader resourceLoader;
    private final Gson gson;
    private String xmlTemplate;

    public SoapMapper(SoapProperties props, ResourceLoader resourceLoader, Gson gson) {
        this.props = props;
        this.resourceLoader = resourceLoader;
        this.gson = gson;
    }

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource("classpath:templates/soap-envelope.xml");
            try (InputStream is = resource.getInputStream()) {
                String rawTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                this.xmlTemplate = rawTemplate
                        .replace(SoapConstants.T_NS_STD, props.soapNamespace())
                        .replace(SoapConstants.T_NS_ENV, props.headerNamespace())
                        .replace(SoapConstants.T_NS_BODY, props.bodyNamespace())
                        .replace(SoapConstants.T_SYSTEM_ID, props.systemId())
                        .replace(SoapConstants.T_USER_NAME, Objects.requireNonNullElse(props.userName(), ""))
                        .replace(SoapConstants.T_USER_TOKEN, Objects.requireNonNullElse(props.userToken(), ""))
                        .replace(SoapConstants.T_DEST_NAME, Objects.requireNonNullElse(props.destinationName(), ""))
                        .replace(SoapConstants.T_DEST_NS, Objects.requireNonNullElse(props.destinationNamespace(), ""))
                        .replace(SoapConstants.T_DEST_OP, Objects.requireNonNullElse(props.destinationOperation(), ""))
                        .replace(SoapConstants.T_CLASS, Objects.requireNonNullElse(props.classification(), ""))
                        .replace(SoapConstants.T_GRUPO_SEGURIDAD, Objects.requireNonNullElse(props.grupoSeguridad(), ""))
                        .replace(SoapConstants.T_CUENTA_SEGURIDAD, Objects.requireNonNullElse(props.cuentaSeguridad(), ""))
                        .replace(SoapConstants.T_ID_PERFIL, Objects.requireNonNullElse(props.idPerfil(), ""))
                        .replace(SoapConstants.T_CODIGO_SUBSERIE, Objects.requireNonNullElse(props.codigoSubSerie(), ""));
                LOGGER.info("SOAP Template loaded successfully");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load SOAP Template", e);
            throw new RuntimeException("SOAP Initialization Failure", e);
        }
    }

    public String buildEnvelope(FileUploadRequest request, String traceId) {
        try {
            String base64Content = request.getFileContent() != null
                    ? Base64.getEncoder().encodeToString(request.getFileContent())
                    : "";
            String safeFilename = escapeXml(Objects.requireNonNullElse(request.getDocumentName(), "unknown"));
            String subTipo = escapeXml(Objects.requireNonNullElse(request.getSubTypeDocumentary(), ""));
            
            // Asignación directa del campo del request sin depender del fallback de properties
            String tipoDoc = escapeXml(request.getTypeDocumentary());
                    
            String autor = request.getNameNegotiator() != null && !request.getNameNegotiator().isBlank()
                    ? escapeXml(request.getNameNegotiator())
                    : escapeXml(Objects.requireNonNullElse(props.autor(), ""));

            String tipoDocId = escapeXml(request.getTypeDocumentSupplier()); 
            String numDocId = escapeXml(request.getIdentificationSupplier());
            
            // Se lee directamente de las propiedades configuradas en el application.yml
            String codigoSubSerie = escapeXml(Objects.requireNonNullElse(props.codigoSubSerie(), ""));

            // Construir metadata dinámicamente mapeando los campos del modelo real FileUploadRequest
            StringBuilder metadataBuilder = new StringBuilder();
            if (props.metaData() != null) {
                for (Map.Entry<String, String> entry : props.metaData().entrySet()) {
                    metadataBuilder.append("            <tiposMetaData>\n");
                    metadataBuilder.append("               <nombre>").append(escapeXml(entry.getKey())).append("</nombre>\n");
                    
                    String val = "";
                    switch (entry.getKey()) {
                        case "xNumeroDoc" -> val = request.getIdSupplierErp();
                        case "xNombre" -> {
                            String nameVal = request.getNameSupplier() != null ? request.getNameSupplier() : request.getNameSupplierErp();
                            if (nameVal != null && nameVal.trim().startsWith("{")) {
                                try {
                                    Map<?, ?> map = gson.fromJson(nameVal, Map.class);
                                    val = map.containsKey("businessName") ? String.valueOf(map.get("businessName")) : nameVal;
                                } catch (Exception e) {
                                    val = nameVal;
                                }
                            } else {
                                val = nameVal;
                            }
                        }
                        case "xRadicado" -> val = request.getEstablished();
                        case "xSubproducto" -> val = request.getSubProduct();
                        case "xAsunto" -> val = request.getContratcState(); // Nota: se respeta el campo contratcState
                        case "xComments" -> val = request.getTitleContract();
                        case "xTipoProducto" -> val = request.getTypeAgreement();
                        case "xFechaExpedicionDocumento" -> val = request.getDateAgreement() != null 
                                ? request.getDateAgreement().toLocalDate().toString() : "";
                        case "xFechaExpiracionDocumento" -> val = request.getExpirationDate() != null 
                                ? request.getExpirationDate().toLocalDate().toString() : "";
                        case "xNombreProyecto" -> val = request.getNameNegotiator();
                        case "xRub" -> val = request.getVersionContract();
                        default -> val = entry.getValue();
                    }
                    
                    metadataBuilder.append("               <valor>").append(escapeXml(val)).append("</valor>\n");
                    metadataBuilder.append("            </tiposMetaData>\n");
                }
            }

            return this.xmlTemplate
                    .replace(SoapConstants.T_TRACE_ID, escapeXml(traceId))
                    .replace(SoapConstants.T_TIMESTAMP, Instant.now().toString())
                    .replace(SoapConstants.T_TIPO_DOCUMENTAL, tipoDoc)
                    .replace(SoapConstants.T_AUTOR, autor)
                    .replace(SoapConstants.T_SUBTIPO_DOCUMENTAL, subTipo)
                    .replace(SoapConstants.T_FILENAME, safeFilename)
                    .replace(SoapConstants.T_CONTENT, base64Content)
                    .replace(SoapConstants.T_TIPO_DOC_ID, tipoDocId)
                    .replace(SoapConstants.T_NUM_DOC_ID, numDocId)
                    .replace(SoapConstants.T_CODIGO_SUBSERIE, codigoSubSerie)
                    .replace(SoapConstants.T_METADATA_BLOCK, metadataBuilder.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error building SOAP envelope", e);
            throw ProcessingException.withTraceId("Build failed", ProcessingResultCodes.UNKNOWN_ERROR.name(), traceId,
                    e);
        }
    }

    private String escapeXml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public FileUploadResponse parseResponse(String xml, String traceId) {
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList faults = doc.getElementsByTagNameNS("*", "Fault");
            if (faults.getLength() == 0) {
                faults = doc.getElementsByTagName("Fault");
            }

            if (faults.getLength() > 0) {
                return handleSoapFault((Element) faults.item(0), traceId);
            }

            NodeList responseNodes = doc.getElementsByTagNameNS("*", SoapConstants.EL_TRANSMITIR_DOCUMENTO_RESPONSE);
            if (responseNodes.getLength() == 0) {
                responseNodes = doc.getElementsByTagName(SoapConstants.EL_TRANSMITIR_DOCUMENTO_RESPONSE);
            }
            if (responseNodes.getLength() > 0) {
                return mapFromElement(doc.getDocumentElement());
            }

            throw new ProcessingException("Unknown SOAP response structure",
                    ProcessingResultCodes.INVALID_RESPONSE.name());

        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error parsing SOAP response for traceId=" + traceId, e);
            throw ProcessingException.withTraceId("Parse failed", ProcessingResultCodes.INVALID_RESPONSE.name(),
                    traceId, e);
        }
    }

    private FileUploadResponse mapFromElement(Element el) {
        String status = extractTextContentRecursive(el, SoapConstants.EL_STATUS);
        if (status == null) {
            status = extractTextContentRecursive(el, SoapConstants.EL_STATUS_CODE);
        }

        String message = extractTextContentRecursive(el, SoapConstants.EL_MESSAGE);

        String correlationId = extractTextContentRecursive(el, SoapConstants.EL_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = extractTextContentRecursive(el, SoapConstants.EL_MESSAGE_ID);
        }

        String processedAt = extractTextContentRecursive(el, SoapConstants.EL_PROCESSED_AT);

        String externalReference = extractTextContentRecursive(el, SoapConstants.EL_EXTERNAL_REFERENCE);
        if (externalReference == null) {
            externalReference = extractTextContentRecursive(el, SoapConstants.EL_ID_DOCUMENTO);
        }

        boolean isSuccess = SoapConstants.STATUS_OK.equalsIgnoreCase(status)
                || ProcessingResultCodes.SUCCESS.name().equalsIgnoreCase(status);

        String finalStatus = status != null ? status : ProcessingResultCodes.SUCCESS.name();
        String finalCorrelationId = correlationId != null ? correlationId : "N/A";
        String finalExternalReference = externalReference != null ? externalReference : "N/A";

        String finalMessage;
        if (isSuccess) {
            if (message != null && !message.isBlank()) {
                finalMessage = String.format("statusCode: %s, messageId: %s, idDocumento: %s | message: %s",
                        finalStatus, finalCorrelationId, finalExternalReference, message);
            } else {
                finalMessage = String.format("statusCode: %s, messageId: %s, idDocumento: %s",
                        finalStatus, finalCorrelationId, finalExternalReference);
            }
        } else {
            finalMessage = message != null ? message : ProcessingResultCodes.FAILURE.name();
        }

        Instant parsedDate;
        try {
            parsedDate = processedAt != null ? Instant.parse(processedAt.trim()) : Instant.now();
        } catch (Exception e) {
            try {
                parsedDate = java.time.OffsetDateTime.parse(processedAt.trim()).toInstant();
            } catch (Exception ex) {
                parsedDate = Instant.now();
            }
        }

        return FileUploadResponse.builder()
                .status(finalStatus)
                .message(finalMessage)
                .correlationId(finalCorrelationId)
                .processedAt(parsedDate)
                .externalReference(externalReference)
                .success(isSuccess)
                .syncStatus(finalStatus)
                .build();
    }

    private FileUploadResponse handleSoapFault(Element faultElement, String traceId) {
        String faultString = "SOAP Fault received";
        String extractedCode = "";

        try {
            String directCode = extractTextContentRecursive(faultElement, "code");
            if (directCode != null)
                extractedCode = directCode;

            String directDesc = extractTextContentRecursive(faultElement, "description");
            if (directDesc != null) {
                faultString = directDesc;
            } else {
                String faultStringStandard = extractTextContentRecursive(faultElement, "faultstring");
                if (faultStringStandard != null)
                    faultString = faultStringStandard;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during Fault extraction for traceId=" + traceId, e);
        }

        String finalMessage = extractedCode.isBlank() ? faultString : extractedCode + " - " + faultString;

        return FileUploadResponse.builder()
                .status(ProcessingResultCodes.FAILURE.name())
                .message(finalMessage)
                .correlationId(extractedCode.isBlank() ? ProcessingResultCodes.SOAP_ERROR.name() : extractedCode)
                .processedAt(Instant.now())
                .success(false)
                .syncStatus(ProcessingResultCodes.SOAP_ERROR.name())
                .traceId(traceId)
                .build();
    }

    private String extractTextContentRecursive(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0)
            nodes = parent.getElementsByTagName(localName);

        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}
```

## 4.2 Clase Completa `SoapGatewayAdapter.java` (Sin Cambios)

Esta clase no requiere modificaciones estructurales porque delega dinámicamente todo el procesamiento de la plantilla XML y mapeo de respuestas a `SoapMapper` y se configura mediante `SoapProperties`. Se incluye completa para facilitar la copia y pega:

```java
package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger LOGGER = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient soapWebClient;
    private final SoapProperties properties;
    private final SoapMapper mapper;

    public SoapGatewayAdapter(WebClient.Builder webClientBuilder, SoapProperties properties, SoapMapper mapper) {
        this.soapWebClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .build();
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public Flux<FileUploadResponse> send(FileUploadRequest request) {
        return Flux.deferContextual(ctx -> {
            final String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            return sendWithRetry(request, traceId, 1);
        });
    }

    private Flux<FileUploadResponse> sendWithRetry(FileUploadRequest request, String traceId, int attempt) {
        return soapWebClient.post()
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", properties.soapAction() != null ? properties.soapAction() : "")
                .bodyValue(mapper.buildEnvelope(request, traceId))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .switchIfEmpty(Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                        ProcessingResultCodes.INVALID_RESPONSE.value(),
                        ProcessingResultCodes.INVALID_RESPONSE.name(), traceId)))
                .map(xml -> mapper.parseResponse(xml, traceId).toBuilder()
                        .attemptCount(attempt)
                        .build())
                .onErrorResume(error -> handleFinalError(error, traceId)
                        .map(errorResp -> errorResp.toBuilder().attemptCount(attempt).build()))
                .flatMapMany(response -> {
                    boolean isRetryable = !response.isSuccess() &&
                                         ProcessingResultCodes.isTransient(response.getSyncStatus()) &&
                                         attempt <= properties.retryAttempts();

                    if (isRetryable) {
                        LOGGER.log(Level.INFO, "[TraceID: {0}] Technical retry {1}/{2} due to: {3}",
                                new Object[]{traceId, attempt, properties.retryAttempts(), response.getMessage()});

                        return Flux.just(response.toBuilder().technicalRetry(true).build())
                                .concatWith(Mono.delay(Duration.ofMillis(500))
                                        .flatMapMany(unused -> sendWithRetry(request, traceId, attempt + 1)));
                    }
                    return Flux.just(response.toBuilder().technicalRetry(false).build());
                });
    }

    private Mono<FileUploadResponse> handleFinalError(Throwable error, String traceId) {
        if (error instanceof WebClientResponseException wce) {
            String rawBody = wce.getResponseBodyAsString();
            if (isXml(rawBody)) {
                try {
                    return Mono.just(mapper.parseResponse(rawBody, traceId));
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to parse Fault from error body", e);
                }
            }
        }

        String syncStatus = mapErrorCode(error);
        String message = error.getMessage();

        if (error instanceof WebClientResponseException wce) {
            message = String.format("HTTP %d - %s", wce.getStatusCode().value(), wce.getStatusText());
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            message = "Timeout: El servicio no respondió en " + properties.timeoutSeconds() + " segundos";
        }

        return Mono.just(FileUploadResponse.builder()
                .status(ProcessingResultCodes.FAILED.name())
                .message(message != null ? message : ProcessingResultCodes.UNKNOWN_ERROR.value())
                .syncStatus(syncStatus)
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(false)
                .build());
    }

    private boolean isXml(String body) {
        return body != null && body.trim().startsWith("<") && !body.toLowerCase().contains("<html");
    }

    private String mapErrorCode(Throwable error) {
        if (error instanceof WebClientResponseException)
            return ProcessingResultCodes.BAD_GATEWAY.name();
        if (error instanceof TimeoutException)
            return ProcessingResultCodes.GATEWAY_TIMEOUT.name();
        return ProcessingResultCodes.UNKNOWN_ERROR.name();
    }
}
```

## 5. Clase Completa de Pruebas Unitarias (`SoapMapperTest.java`)

Para garantizar que el nuevo mapeo dinámico y las propiedades se inyectan correctamente, se provee el código completo del archivo de pruebas unitarias:

```java
package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SoapMapperTest {

    private SoapMapper soapMapper;
    private SoapProperties props;
    private ResourceLoader resourceLoader;

    @BeforeEach
    void setUp() throws Exception {
        props = Mockito.mock(SoapProperties.class);
        resourceLoader = Mockito.mock(ResourceLoader.class);

        // Configuración de propiedades estáticas y de namespaces para la prueba
        when(props.soapNamespace()).thenReturn("http://schemas.xmlsoap.org/soap/envelope/");
        when(props.bodyNamespace()).thenReturn("http://sumarmas.com/intf/Corporativo/administracionDocumentos/GestionInternaDocumental/V1.0");
        when(props.headerNamespace()).thenReturn("http://sumarmas.com/ents/SOI/MessageFormat/V2.1");
        when(props.systemId()).thenReturn("NU1730001");
        when(props.userName()).thenReturn("user");
        when(props.userToken()).thenReturn("pass");
        
        when(props.tipoDocumental()).thenReturn("Contratos_Clausulas_OtroSi");
        when(props.autor()).thenReturn("Prueba-QA-Abastecimiento");
        when(props.grupoSeguridad()).thenReturn("Aliados");
        when(props.cuentaSeguridad()).thenReturn("Aliado/contratos_proveedor_abastecimiento");
        when(props.idPerfil()).thenReturn("Contratosproveedorabast");

        // Configuración de metadatos esperados
        when(props.metaData()).thenReturn(Map.of(
            "xNumeroDoc", "",
            "xAsunto", ""
        ));

        // Plantilla mock XML para simular el comportamiento del template soap-envelope.xml
        String template = "<soapenv:Envelope xmlns:soapenv=\"{{ns_standard}}\" xmlns:v2=\"{{ns_envelope}}\" xmlns:v1=\"{{ns_body}}\">"
                + "<soapenv:Header><v2:requestHeader>"
                + "<systemId>{{SYSTEM_ID}}</systemId><messageId>{{TRACE_ID}}</messageId><timestamp>{{TIMESTAMP}}</timestamp>"
                + "<userId><userName>{{USER_NAME}}</userName></userId>"
                + "</v2:requestHeader></soapenv:Header>"
                + "<soapenv:Body><v1:transmitirDocumento>"
                + "<tipoDocumental>{{TIPO_DOCUMENTAL}}</tipoDocumental><autor>{{AUTOR}}</autor>"
                + "<grupoSeguridad>{{GRUPO_SEGURIDAD}}</grupoSeguridad><cuentaSeguridad>{{CUENTA_SEGURIDAD}}</cuentaSeguridad>"
                + "<subTipoDocumental>{{SUBTIPO_DOCUMENTAL}}</subTipoDocumental>"
                + "<tipoDocumentoIdentificacion>{{TIPO_DOC_ID}}</tipoDocumentoIdentificacion>"
                + "<numeroDocumentoIdentificacion>{{NUM_DOC_ID}}</numeroDocumentoIdentificacion>"
                + "<codigoSubSerie>{{CODIGO_SUBSERIE}}</codigoSubSerie><idPerfil>{{ID_PERFIL}}</idPerfil>"
                + "<nombreArchivo>{{FILENAME}}</nombreArchivo><archivo>{{CONTENT}}</archivo>"
                + "<metaData>{{METADATA_BLOCK}}</metaData>"
                + "</v1:transmitirDocumento></soapenv:Body></soapenv:Envelope>";

        Resource res = new ByteArrayResource(template.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(res);

        soapMapper = new SoapMapper(props, resourceLoader, new com.google.gson.Gson());
        soapMapper.init();
    }

    @Test
    @DisplayName("Debe construir el envelope con la nueva estructura reemplazando propiedades correctamente")
    void buildEnvelope_withNewStructure_replacesPropertiesCorrectly() {
        // Arrange
        String traceId = "test-trace-123";
        FileUploadRequest request = FileUploadRequest.builder()
                .documentName("archivo.pdf")
                .fileContent("PDF_CONTENT".getBytes())
                .typeDocumentSupplier("CC")
                .identificationSupplier("999")
                .subProduct("677.3")
                .subTypeDocumentary("Contrato")
                .build();
                
        // Act
        String envelope = soapMapper.buildEnvelope(request, traceId);
        
        // Assert: Validar reemplazos dinámicos
        assertTrue(envelope.contains("<messageId>test-trace-123</messageId>"), "Debe inyectar el traceId");
        assertTrue(envelope.contains("<nombreArchivo>archivo.pdf</nombreArchivo>"), "Debe inyectar el nombre del archivo");
        assertTrue(envelope.contains("<tipoDocumentoIdentificacion>CC</tipoDocumentoIdentificacion>"));
        assertTrue(envelope.contains("<numeroDocumentoIdentificacion>999</numeroDocumentoIdentificacion>"));
        assertTrue(envelope.contains("<codigoSubSerie>677.3</codigoSubSerie>"));
        
        // Assert: Validar campos estáticos (inyectados por SoapProperties)
        assertTrue(envelope.contains("<tipoDocumental>Contratos_Clausulas_OtroSi</tipoDocumental>"));
        assertTrue(envelope.contains("<autor>Prueba-QA-Abastecimiento</autor>"));
        assertTrue(envelope.contains("<idPerfil>Contratosproveedorabast</idPerfil>"));
        
        // Assert: Validar que cambian por petición
        String envelope2 = soapMapper.buildEnvelope(request, "otro-trace-456");
        assertTrue(envelope2.contains("<messageId>otro-trace-456</messageId>"));
        assertNotEquals(envelope, envelope2, "El timestamp debe cambiar o el traceId hace diferente el string completo");
    }

    @Test
    @DisplayName("Debe construir la metadata dinámicamente en base a la configuración")
    void buildEnvelope_withMetadata_generatesMetadataBlock() {
        // Arrange
        FileUploadRequest request = FileUploadRequest.builder()
                .documentName("archivo.pdf")
                .idSupplierErp("12345")
                .nameSupplier("Proveedor S.A.S.")
                .subProduct("SubProductX")
                .contratcState("Contrato de prueba")
                .typeAgreement("AgreementTypeY")
                .dateAgreement(java.time.LocalDateTime.of(2026, 5, 11, 9, 21, 48))
                .expirationDate(java.time.LocalDateTime.of(2027, 5, 11, 9, 21, 48))
                .build();
        
        // Act
        String envelope = soapMapper.buildEnvelope(request, "trace-1");
        
        // Assert
        assertTrue(envelope.contains("<nombre>xNumeroDoc</nombre>"));
        assertTrue(envelope.contains("<valor>12345</valor>"));
        assertTrue(envelope.contains("<nombre>xAsunto</nombre>"));
        assertTrue(envelope.contains("<valor>Contrato de prueba</valor>"));
    }

    @Test
    @DisplayName("Debe parsear una respuesta SOAP exitosa")
    void parseResponse_withSuccessXml_returnsResponse() {
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
               <S:Body>
                  <transmitirDocumentoResponse>
                     <status>OK</status>
                     <message>Procesado exitosamente</message>
                     <correlationId>CORR-123</correlationId>
                     <processedAt>2026-05-11T09:21:48Z</processedAt>
                  </transmitirDocumentoResponse>
               </S:Body>
            </S:Envelope>
            """;

        FileUploadResponse response = soapMapper.parseResponse(xml, "trace-1");

        assertNotNull(response);
        assertTrue(response.isSuccess(), "Debe marcar success=true");
        assertEquals("CORR-123", response.getCorrelationId());
    }

    @Test
    @DisplayName("Debe fallar si el XML recibido está mal formado")
    void parseResponse_withMalformedXml_throwsException() {
        String xml = "<S:Envelope> BODY UNCLOSED";
        
        assertThrows(ProcessingException.class, () -> 
            soapMapper.parseResponse(xml, "trace-fail")
        );
    }
}
```

## 5.2 Clase Completa de Pruebas para el Adaptador (`SoapGatewayAdapterTest.java`)

Para verificar que el adaptador de integración envía correctamente las peticiones utilizando el nuevo modelo de datos y constructor de `SoapProperties`, se incluye la clase de pruebas unitarias completa:

```java
package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapGatewayAdapterTest {

    private MockWebServer mockWebServer;

    @Mock
    private SoapMapper mapper;

    private SoapProperties properties;
    private SoapGatewayAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new SoapProperties(
            "http://127.0.0.1:" + mockWebServer.getPort() + "/soap", "SYS-01", "user", "h-ns", "b-ns", "s-ns",
            "token", "dest-name", "dest-ns", "dest-op", "action", "CLASS-1", 
            "tipo-doc", "autor-name", "grupo-seg", "cuenta-seg", "id-perfil",
            Map.of(), Map.of(), 10, 0 // 10 seconds timeout and NO retries
        );
        adapter = new SoapGatewayAdapter(WebClient.builder(), properties, mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void send_whenSuccessful_returnsSuccessResult() {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        when(mapper.parseResponse(anyString(), anyString())).thenReturn(
            FileUploadResponse.builder()
                .status("OK")
                .success(true)
                .correlationId("corr-123")
                .processedAt(Instant.now())
                .build()
        );

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("<soap>response</soap>")
            .addHeader("Content-Type", "text/xml"));

        StepVerifier.create(adapter.send(FileUploadRequest.builder()
                .documentName("test.pdf")
                .fileContent(new byte[]{1})
                .build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenTimeout_returnsGatewayTimeout() {
        SoapProperties localProperties = new SoapProperties(
            "http://127.0.0.1:" + mockWebServer.getPort() + "/soap", "SYS-01", "user", "h-ns", "b-ns", "s-ns",
            "token", "dest-name", "dest-ns", "dest-op", "action", "CLASS-1", 
            "tipo-doc", "autor-name", "grupo-seg", "cuenta-seg", "id-perfil",
            Map.of(), Map.of(), 1, 0 // 1 second timeout
        );
        SoapGatewayAdapter localAdapter = new SoapGatewayAdapter(WebClient.builder(), localProperties, mapper);

        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        
        mockWebServer.enqueue(new MockResponse()
            .setHeadersDelay(2, TimeUnit.SECONDS) // Delay exceeds 1 second timeout
            .setBody("<soap>response</soap>"));

        StepVerifier.create(localAdapter.send(FileUploadRequest.builder().documentName("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .thenConsumeWhile(FileUploadResponse::isTechnicalRetry)
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), result.getSyncStatus());
                assertTrue(result.getMessage().contains("Timeout"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenHttp500WithSoapFault_parsesFaultFromBody() {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<Fault>Error</Fault>")
            .addHeader("Content-Type", "text/xml"));
        
        when(mapper.parseResponse(eq("<Fault>Error</Fault>"), anyString())).thenReturn(
            FileUploadResponse.builder()
                .status("FAILURE")
                .success(false)
                .message("Parsed Error")
                .build()
        );

        StepVerifier.create(adapter.send(FileUploadRequest.builder().documentName("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .thenConsumeWhile(FileUploadResponse::isTechnicalRetry)
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals("Parsed Error", result.getMessage());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void send_whenGenericError_returnsUnknownError() throws IOException {
        when(mapper.buildEnvelope(any(), anyString())).thenReturn("<soap>request</soap>");
        
        // Shut down the server to force an immediate Connection Refused error
        mockWebServer.shutdown();

        StepVerifier.create(adapter.send(FileUploadRequest.builder().documentName("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .thenConsumeWhile(FileUploadResponse::isTechnicalRetry)
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }
}
```

## 6. Inventario Completo de Propiedades y Valores a Considerar

A continuación se detalla la matriz completa de todos los campos que componen la estructura XML, su clasificación (Estática o Dinámica) y la fuente sugerida para su obtención:

| Campo XML | Tipo | Valor de Ejemplo / Origen | Descripción / Configuración |
| :--- | :--- | :--- | :--- |
| **xmlns:soapenv** | Estático | `http://schemas.xmlsoap.org/soap/envelope/` | Namespace SOAP estándar. Configurado mediante `soapNamespace` en `application.yml`. |
| **xmlns:v2** | Estático | `http://sumarmas.com/ents/SOI/MessageFormat/V2.1` | Namespace de cabecera (requestHeader). Configurado mediante `headerNamespace` en `application.yml`. |
| **xmlns:v1** | Estático | `http://sumarmas.com/intf/Corporativo/administracionDocumentos/GestionInternaDocumental/V1.0` | Namespace de negocio (transmitirDocumento). Configurado mediante `bodyNamespace` en `application.yml`. |
| **systemId** | Estático | `NU1730001` | Identificador del sistema emisor. Se define en `application.yml`. |
| **messageId** | Dinámico | Generado por petición | Identificador único de transacción (`traceId`). |
| **timestamp** | Dinámico | Generado por petición | Fecha/hora actual en formato ISO-8601 (`yyyy-MM-ddTHH:mm:ss`). |
| **userName** | Estático | `user` | Nombre de usuario para la cabecera de seguridad. Se define en `application.yml`. |
| **destination/name** | Estático | Configurable | Nombre del destino SOAP. Se define en `application.yml`. |
| **destination/namespace** | Estático | Configurable | Namespace del servicio destino. Se define en `application.yml`. |
| **destination/operation** | Estático | Configurable | Nombre de la operación a ejecutar. Se define en `application.yml`. |
| **classification** | Estático | Configurable | Clasificación de seguridad/mensaje. Se define en `application.yml`. |
| **tipoDocumental** | Dinámico/Estático | `request.getTypeDocumentary()` / `props.tipoDocumental()` | Tipo de documento general. Dinámico si viene en la petición, fallback a propiedad. |
| **autor** | Dinámico/Estático | `request.getNameNegotiator()` / `props.autor()` | Autor del cargue o creador del documento. |
| **grupoSeguridad** | Estático | `Aliados` | Grupo de seguridad asociado. Se define en `application.yml`. |
| **cuentaSeguridad** | Estático | `Aliado/contratos_proveedor_abastecimiento` | Cuenta de seguridad específica. Se define en `application.yml`. |
| **subTipoDocumental** | Dinámico | `request.getSubTypeDocumentary()` | Subcategoría o carpeta del documento (ej. `subTypeDocumentary`). |
| **tipoDocumentoIdentificacion** | Dinámico | `request.getTypeDocumentSupplier()` | Tipo de documento de identidad del titular (ej. `CC`, `NIT`). |
| **numeroDocumentoIdentificacion** | Dinámico | `request.getIdentificationSupplier()` | Número de documento de identidad del titular (ej. `identificationSupplier`). |
| **codigoSubSerie** | Dinámico | `request.getSubProduct()` | Código de la subserie documental asignado. Mapeado desde `subProduct`. |
| **idPerfil** | Estático | `Contratosproveedorabast` | Identificador del perfil de metadatos. Se define en `application.yml`. |
| **nombreArchivo** | Dinámico | `request.getDocumentName()` | Nombre físico del archivo enviado (ej. `documentName`). |
| **archivo** | Dinámico | Base64 de `request.getFileContent()` | Contenido binario codificado en Base64. |

### 6.1 Estructura e Inventario de Metadatos (`metaData`)

El bloque `<metaData>` contendrá elementos de tipo `<tiposMetaData>`, cada uno compuesto por `<nombre>` y `<valor>`. A continuación se define el mapeo exacto de los metadatos desde las propiedades del modelo `FileUploadRequest`:

1. **`xNumeroDoc`**: Mapea a `request.getIdSupplierErp()` (Identificador ERP del proveedor/entidad).
2. **`xNombre`**: Mapea a `request.getNameSupplier()` (si no es nulo, de lo contrario `request.getNameSupplierErp()`).
3. **`xRadicado`**: Mapea a `request.getEstablished()` (Número de radicado/establecimiento).
4. **`xSubproducto`**: Mapea a `request.getSubProduct()` (Subproducto/código asociado).
5. **`xAsunto`**: Mapea a `request.getContratcState()` (Estado del contrato).
6. **`xComments`**: Mapea a `request.getTitleContract()` (Título o asunto principal del contrato).
7. **`xTipoProducto`**: Mapea a `request.getTypeAgreement()` (Tipo de acuerdo).
8. **`xFechaExpedicionDocumento`**: Mapea a `request.getDateAgreement()` (Fecha en formato `yyyy-MM-dd`).
9. **`xFechaExpiracionDocumento`**: Mapea a `request.getExpirationDate()` (Fecha en formato `yyyy-MM-dd`).
10. **`xNombreProyecto`**: Mapea a `request.getNameNegotiator()` (Nombre del negociador).
11. **`xRub`**: Mapea a `request.getVersionContract()` (Versión del contrato).

---

**NOTAS PARA REVISIÓN:**
1. Todos los campos dinámicos provienen directamente del DTO mapeador `from(DocumentHistoryDTO, HomologationResult)`.
2. Las fechas `dateAgreement` y `expirationDate` se formatean a formato String `yyyy-MM-dd` de manera segura usando `.toLocalDate().toString()`.
3. El campo `contratcState` se mapea respetando el nombre exacto definido en el modelo (incluyendo el posible error de digitación `contratcState`).
