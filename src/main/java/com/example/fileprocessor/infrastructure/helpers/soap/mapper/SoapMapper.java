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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapMapper {

    private static final Logger LOGGER = Logger.getLogger(SoapMapper.class.getName());

    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();
    static {
        FACTORY.setNamespaceAware(true);
    }

    private final SoapProperties props;
    private final ResourceLoader resourceLoader;
    private String xmlTemplate;

    public SoapMapper(SoapProperties props, ResourceLoader resourceLoader) {
        this.props = props;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource("classpath:templates/soap-envelope.xml");
            try (InputStream is = resource.getInputStream()) {
                String rawTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                this.xmlTemplate = rawTemplate
                        .replace(SoapConstants.T_NS_STD, props.soapNamespace())
                        .replace(SoapConstants.T_NS_BODY, props.bodyNamespace())
                        .replace(SoapConstants.T_NS_ENV, props.headerNamespace())
                        .replace(SoapConstants.T_SYSTEM_ID, props.systemId())
                        .replace(SoapConstants.T_USER_NAME, Objects.requireNonNullElse(props.userName(), ""))
                        .replace(SoapConstants.T_USER_TOKEN, Objects.requireNonNullElse(props.userToken(), ""))
                        .replace(SoapConstants.T_DEST_NAME, Objects.requireNonNullElse(props.destinationName(), ""))
                        .replace(SoapConstants.T_DEST_NS, Objects.requireNonNullElse(props.destinationNamespace(), ""))
                        .replace(SoapConstants.T_DEST_OP, Objects.requireNonNullElse(props.destinationOperation(), ""))
                        .replace(SoapConstants.T_CLASS, Objects.requireNonNullElse(props.classification(), ""));
                LOGGER.info("SOAP Template loaded successfully");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load SOAP Template", e);
            throw new RuntimeException("SOAP Initialization Failure", e);
        }
    }

    public String buildEnvelope(FileUploadRequest request, String traceId) {
        try {
            String base64Content = request.getContent() != null
                    ? Base64.getEncoder().encodeToString(request.getContent())
                    : "";
            String safeFilename = escapeXml(Objects.requireNonNullElse(request.getFilename(), "unknown"));
            String subTipo = request.getHomologationFolder() != null ? request.getHomologationFolder()
                    : request.getOriginFolder();
            String safeSubtype = escapeXml(subTipo);

            String catHom = request.getCategoriaDocument() != null ? request.getCategoriaDocument() : "";
            String paisHom = request.getHomologationCountry() != null ? request.getHomologationCountry() : "";
            String carpHom = request.getHomologationFolder() != null ? request.getHomologationFolder() : "";

            String fecha = java.time.LocalDate.now().toString();
            String comentario = SoapConstants.VAL_DEFAULT_COMENTARIO;

            return this.xmlTemplate
                    .replace(SoapConstants.T_TRACE_ID, escapeXml(traceId))
                    .replace(SoapConstants.T_TIMESTAMP, Instant.now().toString())
                    .replace(SoapConstants.T_SUBTYPE, safeSubtype)
                    .replace(SoapConstants.T_FILENAME, safeFilename)
                    .replace(SoapConstants.T_CAT_HOM, escapeXml(catHom))
                    .replace(SoapConstants.T_PAIS_HOM, escapeXml(paisHom))
                    .replace(SoapConstants.T_CARP_HOM, escapeXml(carpHom))
                    .replace(SoapConstants.T_META_NAME_FECHA, SoapConstants.VAL_META_NAME_FECHA)
                    .replace(SoapConstants.T_FECHA, escapeXml(fecha))
                    .replace(SoapConstants.T_META_NAME_COMENTARIO, SoapConstants.VAL_META_NAME_COMENTARIO)
                    .replace(SoapConstants.T_COMENTARIO, escapeXml(comentario))
                    .replace(SoapConstants.T_CONTENT, base64Content);
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

        boolean isSuccess = SoapConstants.STATUS_OK.equalsIgnoreCase(status) || ProcessingResultCodes.SUCCESS.name().equalsIgnoreCase(status);

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

        return FileUploadResponse.builder()
                .status(finalStatus)
                .message(finalMessage)
                .correlationId(finalCorrelationId)
                .processedAt(processedAt != null ? Instant.parse(processedAt) : Instant.now())
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

    /**
     * Busca recursivamente el contenido de texto de una etiqueta por su nombre
     * local en cualquier nivel.
     */
    private String extractTextContentRecursive(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0)
            nodes = parent.getElementsByTagName(localName);

        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    /**
     * Busca recursivamente un nodo por su nombre local.
     */
    private Node findNodeRecursive(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0)
            nodes = parent.getElementsByTagName(localName);
        return (nodes.getLength() > 0) ? nodes.item(0) : null;
    }
}
