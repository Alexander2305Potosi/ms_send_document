package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.SoapFaultDetail;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
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
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapMapper {

    private static final Logger LOGGER = Logger.getLogger(SoapMapper.class.getName());

    private final JAXBContext jaxbContext;
    private final SoapProperties props;
    private final ResourceLoader resourceLoader;
    private String xmlTemplate;

    public SoapMapper(JAXBContext jaxbContext, SoapProperties props, ResourceLoader resourceLoader) {
        this.jaxbContext = jaxbContext;
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

    public String buildEnvelope(FileUploadRequest request, SoapProperties properties, String traceId) {
        try {
            String base64Content = request.getContent() != null ? Base64.getEncoder().encodeToString(request.getContent()) : "";
            String safeFilename = escapeXml(Objects.requireNonNullElse(request.getFilename(), "unknown"));
            String safeOrigin = escapeXml(request.getOrigin());

            return this.xmlTemplate
                .replace(SoapConstants.T_TRACE_ID, escapeXml(traceId))
                .replace(SoapConstants.T_TIMESTAMP, Instant.now().toString())
                .replace(SoapConstants.T_SUBTYPE, safeOrigin)
                .replace(SoapConstants.T_FILENAME, safeFilename)
                .replace(SoapConstants.T_CONTENT, base64Content)
                .replace(SoapConstants.T_METADATA, !props.metaData().isEmpty() ? generateMetadataXml(props.metaData()) : "");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error building SOAP envelope", e);
            throw ProcessingException.withTraceId("Build failed", ProcessingResultCodes.UNKNOWN_ERROR.name(), traceId, e);
        }
    }

    private String generateMetadataXml(Map<String, String> metaData) throws Exception {
        MetaDataWrapper wrapper = new MetaDataWrapper(
            metaData.entrySet().stream().map(e -> new MetaDataEntry(e.getKey(), e.getValue())).toList()
        );
        StringWriter sw = new StringWriter();
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(wrapper, sw);
        return sw.toString();
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    public FileUploadResponse parseResponse(String xml, String traceId) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            
            try {
                SoapEnvelope envelope = (SoapEnvelope) unmarshaller.unmarshal(new StringReader(xml));
                if (envelope != null && envelope.getBody() != null && envelope.getBody().getAny() != null) {
                    Object bodyAny = envelope.getBody().getAny();
                    if (bodyAny instanceof TransmitirDocumentoResponse) {
                        return mapToExternalResponse((TransmitirDocumentoResponse) bodyAny);
                    }
                    if (bodyAny instanceof Element) {
                        return handleSoapFault((Element) bodyAny, unmarshaller, traceId);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Standard JAXB unmarshal failed for response parsing: {0}", e.getMessage());
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            
            NodeList faults = doc.getElementsByTagNameNS("*", "Fault");
            if (faults.getLength() == 0) faults = doc.getElementsByTagName("Fault");
            
            if (faults.getLength() > 0) {
                return handleSoapFault((Element) faults.item(0), unmarshaller, traceId);
            }

            throw new ProcessingException("Unknown SOAP response structure", ProcessingResultCodes.INVALID_RESPONSE.name());

        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error parsing SOAP response for traceId=" + traceId, e);
            throw ProcessingException.withTraceId("Parse failed", ProcessingResultCodes.INVALID_RESPONSE.name(), traceId, e);
        }
    }

    private FileUploadResponse mapToExternalResponse(TransmitirDocumentoResponse response) {
        String status = Objects.requireNonNullElse(response.getStatus(), "SUCCESS");
        boolean isSuccess = "OK".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
        
        return FileUploadResponse.builder()
            .status(status)
            .message(Objects.requireNonNullElse(response.getMessage(), "Success"))
            .correlationId(Objects.requireNonNullElse(response.getCorrelationId(), "N/A"))
            .processedAt(response.getProcessedAt() != null ? Instant.parse(response.getProcessedAt()) : Instant.now())
            .externalReference(response.getExternalReference())
            .success(isSuccess)
            .build();
    }

    private FileUploadResponse handleSoapFault(Element faultElement, Unmarshaller unmarshaller, String traceId) {
        String faultString = "SOAP Fault received";
        String errorCode = ProcessingResultCodes.SOAP_ERROR.name();

        try {
            // 1. Intento mandatorio: Mapeo a SoapFaultDetail
            Node detailNode = findNodeRecursive(faultElement, "detail");
            if (detailNode != null) {
                try {
                    SoapFaultDetail faultDetail = unmarshaller.unmarshal(detailNode, SoapFaultDetail.class).getValue();
                    if (faultDetail != null && faultDetail.getSystemException() != null 
                        && faultDetail.getSystemException().getGenericException() != null) {
                        
                        var genEx = faultDetail.getSystemException().getGenericException();
                        if (genEx.getCode() != null) errorCode = genEx.getCode();
                        if (genEx.getDescription() != null) faultString = genEx.getDescription();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "SoapFaultDetail unmarshal failed, falling back to manual extraction");
                }
            }
            
            // 2. Fallback manual: Búsqueda agresiva de etiquetas code/description en todo el árbol
            if (errorCode.equals(ProcessingResultCodes.SOAP_ERROR.name())) {
                String directCode = extractTextContentRecursive(faultElement, "code");
                if (directCode != null) errorCode = directCode;
            }
            
            if (faultString.equals("SOAP Fault received") || faultString.isBlank()) {
                String directDesc = extractTextContentRecursive(faultElement, "description");
                if (directDesc != null) {
                    faultString = directDesc;
                } else {
                    String faultStringStandard = extractTextContentRecursive(faultElement, "faultstring");
                    if (faultStringStandard != null) faultString = faultStringStandard;
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during Fault extraction for traceId=" + traceId, e);
        }

        return FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .message(faultString)
            .correlationId(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .errorCode(ProcessingResultCodes.SOAP_ERROR.name())
            .traceId(traceId)
            .build();
    }

    /**
     * Busca recursivamente el contenido de texto de una etiqueta por su nombre local en cualquier nivel.
     */
    private String extractTextContentRecursive(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = parent.getElementsByTagName(localName);
        
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
        if (nodes.getLength() == 0) nodes = parent.getElementsByTagName(localName);
        return (nodes.getLength() > 0) ? nodes.item(0) : null;
    }
}
