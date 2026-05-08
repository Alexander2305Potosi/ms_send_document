package com.example.fileprocessor.infrastructure.helpers.soap.v2.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.config.SoapV2Properties;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.constants.SoapV2Constants;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.TransmitirDocumentoRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.TransmitirDocumentoResponse;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2RequestHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapV2Mapper {

    private static final Logger log = Logger.getLogger(SoapV2Mapper.class.getName());
    
    // Shared thread-safe factory to improve performance
    private static final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newInstance();

    private final SoapEnvelopeWrapper envelopeWrapper;
    private final JAXBContext jaxbContext;

    public SoapV2Mapper(SoapEnvelopeWrapper envelopeWrapper, JAXBContext jaxbContext) {
        this.envelopeWrapper = envelopeWrapper;
        this.jaxbContext = jaxbContext;
    }

    public String buildEnvelope(FileUploadRequest request, SoapV2Properties props, String traceId) {
        try {
            StringWriter sw = new StringWriter();
            XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(sw);

            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);

            // ── Envelope ─────────────────────────────────────────────────────
            writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            writer.setPrefix(SoapV2Constants.PREFIX_SOAPENV, SoapV2Constants.SOAP_ENVELOPE_NS);
            writer.setPrefix(SoapV2Constants.PREFIX_HEADER_NS, props.headerNamespace());
            writer.setPrefix(SoapV2Constants.PREFIX_BODY_NS,   props.bodyNamespace());
            writer.writeStartElement(SoapV2Constants.PREFIX_SOAPENV,
                    SoapV2Constants.EL_ENVELOPE, SoapV2Constants.SOAP_ENVELOPE_NS);
            writer.writeNamespace(SoapV2Constants.PREFIX_SOAPENV, SoapV2Constants.SOAP_ENVELOPE_NS);
            writer.writeNamespace(SoapV2Constants.PREFIX_HEADER_NS, props.headerNamespace());
            writer.writeNamespace(SoapV2Constants.PREFIX_BODY_NS,   props.bodyNamespace());

            // ── Header ───────────────────────────────────────────────────────
            writer.writeStartElement(SoapV2Constants.SOAP_ENVELOPE_NS, SoapV2Constants.EL_HEADER);
            JAXBElement<SoapV2RequestHeader> headerEl = new JAXBElement<>(
                    new QName(props.headerNamespace(), SoapV2Constants.EL_REQUEST_HEADER,
                              SoapV2Constants.PREFIX_HEADER_NS),
                    SoapV2RequestHeader.class,
                    buildHeader(props, traceId));
            marshaller.marshal(headerEl, writer);
            writer.writeEndElement();

            // ── Body ─────────────────────────────────────────────────────────
            writer.writeStartElement(SoapV2Constants.SOAP_ENVELOPE_NS, SoapV2Constants.EL_BODY);
            JAXBElement<TransmitirDocumentoRequest> bodyEl = new JAXBElement<>(
                    new QName(props.bodyNamespace(), SoapV2Constants.EL_TRANSMITIR_DOCUMENTO,
                              SoapV2Constants.PREFIX_BODY_NS),
                    TransmitirDocumentoRequest.class,
                    buildBodyRequest(request, props));
            marshaller.marshal(bodyEl, writer);
            writer.writeEndElement();

            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            return sw.toString();

        } catch (JAXBException | XMLStreamException e) {
            log.log(Level.SEVERE, "Error building SOAP V2 envelope for traceId=" + traceId, e);
            throw ProcessingException.withTraceId(
                "Failed to build SOAP V2 envelope", ProcessingResultCodes.UNKNOWN_ERROR, traceId, e);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Unexpected error building SOAP V2 envelope for traceId=" + traceId, e);
            throw ProcessingException.withTraceId(
                "Unexpected error building SOAP V2 envelope: " + e.getMessage(),
                ProcessingResultCodes.UNKNOWN_ERROR, traceId, e);
        }
    }

    private SoapV2RequestHeader buildHeader(SoapV2Properties props, String traceId) {
        SoapV2RequestHeader header = new SoapV2RequestHeader();
        header.setSystemId(props.systemId());
        header.setMessageId(traceId);
        header.setTimestamp(Instant.now().toString());

        if (!props.messageContext().isEmpty()) {
            header.setMessageContext(new com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2MessageContext(
                props.messageContext().entrySet().stream()
                    .map(e -> new com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2MessageProperty(e.getKey(), e.getValue()))
                    .toList()
            ));
        }

        header.setUserId(new com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2UserId(
            props.userName(), props.userToken()));

        if (props.destinationName() != null && !props.destinationName().isBlank()) {
            header.setDestination(new com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2Destination(
                props.destinationName(), props.destinationNamespace(), props.destinationOperation()));
        }

        if (!props.classifications().isEmpty()) {
            header.setClassifications(new com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2Classifications(
                props.classifications()));
        }

        return header;
    }

    private TransmitirDocumentoRequest buildBodyRequest(FileUploadRequest request, SoapV2Properties props) {
        String base64Content = request.getContent() != null
            ? Base64.getEncoder().encodeToString(request.getContent())
            : "";
        String safeFilename = request.getFilename() != null ? request.getFilename() : "unknown";

        return new TransmitirDocumentoRequest(
            request.getSubTipoDocumental(),
            safeFilename,
            base64Content,
            buildMetaDataWrapper(props)
        );
    }

    private MetaDataWrapper buildMetaDataWrapper(SoapV2Properties props) {
        if (props.metaData().isEmpty()) return null;
        return new MetaDataWrapper(props.metaData().entrySet().stream()
            .map(e -> new MetaDataEntry(e.getKey(), e.getValue()))
            .toList());
    }

    public ExternalServiceResponse parseResponse(String xml, String traceId) {
        try {
            TransmitirDocumentoResponse response = envelopeWrapper.unwrapResponse(
                xml, TransmitirDocumentoResponse.class);

            Instant processedAt;
            try {
                processedAt = response.getProcessedAt() != null
                    ? Instant.parse(response.getProcessedAt())
                    : Instant.now();
            } catch (DateTimeParseException e) {
                log.log(Level.WARNING, "TraceId {0}: Failed to parse processedAt date [{1}]. Using current time.",
                    new Object[]{traceId, response.getProcessedAt()});
                processedAt = Instant.now();
            }

            return ExternalServiceResponse.builder()
                .status(Objects.requireNonNullElse(response.getStatus(), "UNKNOWN"))
                .message(Objects.requireNonNullElse(response.getMessage(), "No message received"))
                .correlationId(Objects.requireNonNullElse(response.getCorrelationId(), "N/A"))
                .processedAt(processedAt)
                .externalReference(response.getExternalReference())
                .build();
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error parsing SOAP V2 response for traceId=" + traceId, e);
            throw ProcessingException.withTraceId(
                "Failed to parse SOAP V2 response: " + e.getMessage(),
                ProcessingResultCodes.INVALID_RESPONSE, traceId, e);
        }
    }
}