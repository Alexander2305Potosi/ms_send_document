package com.example.fileprocessor.infrastructure.helpers.soap.v2.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.config.SoapV2Properties;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.constants.SoapV2Constants;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.NamespaceInjectingStreamWriter;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2Classifications;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2Destination;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2MessageContext;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2MessageProperty;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2RequestHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2UserId;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.TransmitirDocumentoRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapV2Mapper {

    private static final Logger log = Logger.getLogger(SoapV2Mapper.class.getName());

    private final SoapEnvelopeWrapper envelopeWrapper;
    private final JAXBContext jaxbContext;
    private final XMLOutputFactory xmlOutputFactory;

    public SoapV2Mapper(SoapEnvelopeWrapper envelopeWrapper) {
        this.envelopeWrapper = envelopeWrapper;
        this.jaxbContext = envelopeWrapper.getJaxbContext();
        this.xmlOutputFactory = XMLOutputFactory.newInstance();
    }

    public String buildEnvelope(FileUploadRequest request, SoapV2Properties props, String traceId) {
        try {
            StringWriter stringWriter = new StringWriter();
            XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(stringWriter);
            startEnvelope(writer, props);
            writeHeader(writer, props, traceId);
            writeBody(writer, request, props);
            endEnvelope(writer);
            return stringWriter.toString();
        } catch (XMLStreamException | JAXBException e) {
            log.log(Level.SEVERE, "Error building SOAP V2 envelope: {0}", e.getMessage());
            throw ProcessingException.withTraceId(
                "Failed to build SOAP V2 envelope", ProcessingResultCodes.UNKNOWN_ERROR, traceId, e);
        }
    }

    private void startEnvelope(XMLStreamWriter writer, SoapV2Properties props)
            throws XMLStreamException {
        writer.writeStartDocument("UTF-8", "1.0");

        writer.setPrefix(SoapV2Constants.PREFIX_SOAPENV, SoapV2Constants.SOAP_ENVELOPE_NS);
        writer.setPrefix(SoapV2Constants.PREFIX_V2, props.headerNamespace());
        writer.setPrefix(SoapV2Constants.PREFIX_V1, props.bodyNamespace());

        writer.writeStartElement(SoapV2Constants.SOAP_ENVELOPE_NS, SoapV2Constants.EL_ENVELOPE);
        writer.writeNamespace(SoapV2Constants.PREFIX_SOAPENV, SoapV2Constants.SOAP_ENVELOPE_NS);
        writer.writeNamespace(SoapV2Constants.PREFIX_V2, props.headerNamespace());
        writer.writeNamespace(SoapV2Constants.PREFIX_V1, props.bodyNamespace());
    }

    private void endEnvelope(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement(); // Envelope
        writer.writeEndDocument();
        writer.close();
    }

    private void writeHeader(XMLStreamWriter writer, SoapV2Properties props, String traceId)
            throws XMLStreamException, JAXBException {
        writer.writeStartElement(SoapV2Constants.SOAP_ENVELOPE_NS, SoapV2Constants.EL_HEADER);
        writer.writeStartElement(props.headerNamespace(), SoapV2Constants.EL_REQUEST_HEADER);

        SoapV2RequestHeader header = buildHeader(props, traceId);

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);

        XMLStreamWriter injectingWriter = new NamespaceInjectingStreamWriter(
            writer, props.headerNamespace());
        marshaller.marshal(header, injectingWriter);

        writer.writeEndElement(); // requestHeader
        writer.writeEndElement(); // Header
    }

    private SoapV2RequestHeader buildHeader(SoapV2Properties props, String traceId) {
        SoapV2RequestHeader header = new SoapV2RequestHeader();
        header.setSystemId(props.systemId());
        header.setMessageId(traceId);
        header.setTimestamp(Instant.now().toString());

        if (!props.messageContext().isEmpty()) {
            List<SoapV2MessageProperty> properties = props.messageContext().entrySet().stream()
                .map(e -> new SoapV2MessageProperty(e.getKey(), e.getValue()))
                .toList();
            header.setMessageContext(new SoapV2MessageContext(properties));
        }

        SoapV2UserId userId = new SoapV2UserId();
        userId.setUserName(props.userName());
        String token = props.userToken();
        userId.setUserToken(token != null && !token.isBlank() ? token : null);
        header.setUserId(userId);

        String destName = props.destinationName();
        if (destName != null && !destName.isBlank()) {
            SoapV2Destination dest = new SoapV2Destination();
            dest.setName(destName);
            String destNs = props.destinationNamespace();
            String destOp = props.destinationOperation();
            dest.setNamespace(destNs != null && !destNs.isBlank() ? destNs : null);
            dest.setOperation(destOp != null && !destOp.isBlank() ? destOp : null);
            header.setDestination(dest);
        }

        if (!props.classifications().isEmpty()) {
            header.setClassifications(new SoapV2Classifications(props.classifications()));
        }

        return header;
    }

    private void writeBody(XMLStreamWriter writer, FileUploadRequest request, SoapV2Properties props)
            throws XMLStreamException, JAXBException {
        writer.writeStartElement(SoapV2Constants.SOAP_ENVELOPE_NS, SoapV2Constants.EL_BODY);

        TransmitirDocumentoRequest bodyRequest = buildBodyRequest(request, props);

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);

        marshaller.marshal(bodyRequest, writer);

        writer.writeEndElement(); // Body
    }

    private TransmitirDocumentoRequest buildBodyRequest(FileUploadRequest request, SoapV2Properties props) {
        String base64Content = request.getContent() != null
            ? Base64.getEncoder().encodeToString(request.getContent())
            : "";

        String safeFilename = request.getFilename() != null
            ? request.getFilename()
            : "unknown";

        return new TransmitirDocumentoRequest(
            props.subTipoDocumental(),
            safeFilename,
            base64Content,
            buildMetaDataWrapper(props)
        );
    }

    private MetaDataWrapper buildMetaDataWrapper(SoapV2Properties props) {
        if (props.metaData().isEmpty()) return null;
        List<MetaDataEntry> entries = props.metaData().entrySet().stream()
            .map(e -> new MetaDataEntry(e.getKey(), e.getValue()))
            .toList();
        return new MetaDataWrapper(entries);
    }


    public ExternalServiceResponse parseResponse(String xml) {
        try {
            TransmitirDocumentoResponse response = envelopeWrapper.unwrapResponse(
                xml, TransmitirDocumentoResponse.class);

            Instant processedAt = response.getProcessedAt() != null
                ? Instant.parse(response.getProcessedAt())
                : Instant.now();

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
            log.log(Level.SEVERE, "Error parsing SOAP V2 response: {0}", e.getMessage());
            throw ProcessingException.withTraceId(
                "Failed to parse SOAP V2 response: " + e.getMessage(),
                ProcessingResultCodes.INVALID_RESPONSE, "", e);
        }
    }
}
