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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

                LOGGER.info("SOAP Physical Template loaded and pre-configured successfully");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load SOAP Physical Template", e);
            throw new RuntimeException("SOAP Initialization Failure", e);
        }
    }

    public String buildEnvelope(FileUploadRequest request, SoapProperties properties, String traceId) {
        try {
            String base64Content = request.getContent() != null ? Base64.getEncoder().encodeToString(request.getContent()) : "";
            String safeFilename = escapeXml(Objects.requireNonNullElse(request.getFilename(), "unknown"));
            String safeSubtype = escapeXml(request.getSubTipoDocumental());

            String result = this.xmlTemplate
                .replace(SoapConstants.T_TRACE_ID, escapeXml(traceId))
                .replace(SoapConstants.T_TIMESTAMP, Instant.now().toString())
                .replace(SoapConstants.T_SUBTYPE, safeSubtype)
                .replace(SoapConstants.T_FILENAME, safeFilename)
                .replace(SoapConstants.T_CONTENT, base64Content);

            if (!props.metaData().isEmpty()) {
                result = result.replace(SoapConstants.T_METADATA, generateMetadataXml(props.metaData()));
            } else {
                result = result.replace(SoapConstants.T_METADATA, "");
            }

            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error building SOAP envelope for traceId=" + traceId, e);
            throw ProcessingException.withTraceId(
                "Failed to build SOAP envelope: " + e.getMessage(), ProcessingResultCodes.UNKNOWN_ERROR.name(), traceId, e);
        }
    }

    private String generateMetadataXml(Map<String, String> metaData) throws Exception {
        MetaDataWrapper wrapper = new MetaDataWrapper(
            metaData.entrySet().stream()
                .map(e -> new MetaDataEntry(e.getKey(), e.getValue()))
                .toList()
        );
        StringWriter sw = new StringWriter();
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(wrapper, sw);
        return sw.toString();
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }

    public ExternalServiceResponse parseResponse(String xml, String traceId) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            SoapEnvelope envelope = (SoapEnvelope) unmarshaller.unmarshal(new StringReader(xml));
            
            if (envelope.getBody() == null || envelope.getBody().getAny() == null) {
                throw new ProcessingException("Invalid SOAP response: missing body content", ProcessingResultCodes.INVALID_RESPONSE.name());
            }

            Object bodyAny = envelope.getBody().getAny();

            // Caso 1: Respuesta exitosa estándar
            if (bodyAny instanceof TransmitirDocumentoResponse) {
                return mapToExternalResponse((TransmitirDocumentoResponse) bodyAny);
            }

            // Caso 2: El cuerpo es un Nodo DOM (Puede ser Fault o la respuesta sin mapear)
            if (bodyAny instanceof Element) {
                Element element = (Element) bodyAny;
                
                // ¿Es un SOAP Fault?
                if (element.getLocalName().equals("Fault")) {
                    return handleSoapFault(element, unmarshaller, traceId);
                }

                // Intentar unmarshal del elemento como respuesta exitosa
                try {
                    TransmitirDocumentoResponse res = unmarshaller.unmarshal(element, TransmitirDocumentoResponse.class).getValue();
                    return mapToExternalResponse(res);
                } catch (Exception ignored) {
                    // Si falla, seguimos al error genérico
                }
            }

            throw new ProcessingException("Unexpected body content in SOAP response", ProcessingResultCodes.INVALID_RESPONSE.name());

        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing SOAP response for traceId=" + traceId, e);
            throw ProcessingException.withTraceId("Failed to parse SOAP response: " + e.getMessage(), ProcessingResultCodes.INVALID_RESPONSE.name(), traceId, e);
        }
    }

    private ExternalServiceResponse mapToExternalResponse(TransmitirDocumentoResponse response) {
        return ExternalServiceResponse.builder()
            .status(Objects.requireNonNullElse(response.getStatus(), "UNKNOWN"))
            .message(Objects.requireNonNullElse(response.getMessage(), "No message received"))
            .correlationId(Objects.requireNonNullElse(response.getCorrelationId(), "N/A"))
            .processedAt(response.getProcessedAt() != null ? Instant.parse(response.getProcessedAt()) : Instant.now())
            .externalReference(response.getExternalReference())
            .build();
    }

    private ExternalServiceResponse handleSoapFault(Element faultElement, Unmarshaller unmarshaller, String traceId) {
        String faultString = "SOAP Fault received";
        String errorCode = "SOAP_FAULT";

        try {
            NodeList details = faultElement.getElementsByTagName("detail");
            if (details.getLength() > 0) {
                Element detailElement = (Element) details.item(0);
                // Intentamos parsear el detalle específico que definimos
                SoapFaultDetail faultDetail = unmarshaller.unmarshal(detailElement, SoapFaultDetail.class).getValue();
                
                if (faultDetail != null && faultDetail.getSystemException() != null 
                    && faultDetail.getSystemException().getGenericException() != null) {
                    
                    var genEx = faultDetail.getSystemException().getGenericException();
                    errorCode = genEx.getCode();
                    faultString = genEx.getDescription();
                }
            } else {
                NodeList faultStrings = faultElement.getElementsByTagName("faultstring");
                if (faultStrings.getLength() > 0) {
                    faultString = faultStrings.item(0).getTextContent();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not parse SOAP Fault details for traceId=" + traceId, e);
        }

        return ExternalServiceResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .message(faultString)
            .correlationId(errorCode) // Usamos el código de error como correlationId para trazabilidad
            .processedAt(Instant.now())
            .build();
    }
}
