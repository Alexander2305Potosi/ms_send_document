package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
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

    public ExternalServiceResponse parseResponse(String xml, String traceId) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            
            // INTENTO 1: Deserializar como SoapEnvelope (Caso normal)
            try {
                SoapEnvelope envelope = (SoapEnvelope) unmarshaller.unmarshal(new StringReader(xml));
                if (envelope != null && envelope.getBody() != null && envelope.getBody().getAny() != null) {
                    Object bodyAny = envelope.getBody().getAny();
                    if (bodyAny instanceof TransmitirDocumentoResponse) {
                        return mapToExternalResponse((TransmitirDocumentoResponse) bodyAny);
                    }
                    if (bodyAny instanceof Element) {
                        Element element = (Element) bodyAny;
                        String localName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
                        if (localName.toLowerCase().contains("fault")) {
                            return handleSoapFault(element, unmarshaller, traceId);
                        }
                        // Si no es fault, intentamos unmarshal a la respuesta esperada
                        try {
                            TransmitirDocumentoResponse res = unmarshaller.unmarshal(element, TransmitirDocumentoResponse.class).getValue();
                            return mapToExternalResponse(res);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Standard JAXB unmarshal failed, falling back to DOM parsing for traceId=" + traceId);
            }

            // INTENTO 2: Deserializar usando DOM directamente (Caso de errores mal formados o namespaces atípicos)
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            
            // Buscar 'Fault' en cualquier parte del documento
            NodeList faults = doc.getElementsByTagNameNS("*", "Fault");
            if (faults.getLength() == 0) faults = doc.getElementsByTagName("Fault");
            if (faults.getLength() == 0) faults = doc.getElementsByTagNameNS("*", "fault");
            
            if (faults.getLength() > 0) {
                return handleSoapFault((Element) faults.item(0), unmarshaller, traceId);
            }

            // Si llegamos aquí y no hay nada reconocible
            throw new ProcessingException("Unknown SOAP response structure", ProcessingResultCodes.INVALID_RESPONSE.name());

        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error parsing SOAP response for traceId=" + traceId, e);
            throw ProcessingException.withTraceId("Parse failed: " + e.getMessage(), ProcessingResultCodes.INVALID_RESPONSE.name(), traceId, e);
        }
    }

    private ExternalServiceResponse mapToExternalResponse(TransmitirDocumentoResponse response) {
        return ExternalServiceResponse.builder()
            .status(Objects.requireNonNullElse(response.getStatus(), ProcessingResultCodes.SUCCESS.name()))
            .message(Objects.requireNonNullElse(response.getMessage(), "Success"))
            .correlationId(Objects.requireNonNullElse(response.getCorrelationId(), "N/A"))
            .processedAt(response.getProcessedAt() != null ? Instant.parse(response.getProcessedAt()) : Instant.now())
            .externalReference(response.getExternalReference())
            .build();
    }

    private ExternalServiceResponse handleSoapFault(Element faultElement, Unmarshaller unmarshaller, String traceId) {
        String faultString = "SOAP Fault";
        String errorCode = "SOAP_ERROR";

        try {
            Node detailNode = findNodeByName(faultElement, "detail");
            if (detailNode != null) {
                try {
                    SoapFaultDetail faultDetail = unmarshaller.unmarshal(detailNode, SoapFaultDetail.class).getValue();
                    if (faultDetail != null && faultDetail.getSystemException() != null 
                        && faultDetail.getSystemException().getGenericException() != null) {
                        var genEx = faultDetail.getSystemException().getGenericException();
                        errorCode = Objects.requireNonNullElse(genEx.getCode(), errorCode);
                        faultString = Objects.requireNonNullElse(genEx.getDescription(), faultString);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Detail unmarshal failed, looking for text content");
                    faultString = detailNode.getTextContent();
                }
            }
            
            if (faultString.equals("SOAP Fault") || faultString.isBlank()) {
                Node faultStringNode = findNodeByName(faultElement, "faultstring");
                if (faultStringNode != null) {
                    faultString = faultStringNode.getTextContent();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting Fault details", e);
        }

        return ExternalServiceResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name()) 
            .message(faultString != null && !faultString.isBlank() ? faultString : "SOAP Fault without description")
            .correlationId(errorCode)
            .processedAt(Instant.now())
            .build();
    }

    private Node findNodeByName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String nodeName = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (nodeName.contains(":")) {
                nodeName = nodeName.substring(nodeName.indexOf(":") + 1);
            }
            if (nodeName.equalsIgnoreCase(localName)) {
                return child;
            }
        }
        return null;
    }
}
