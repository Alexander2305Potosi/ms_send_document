package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapBody;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapClassifications;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapDestination;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapMessageContext;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapMessageProperty;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapRequestHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapUserId;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapMapper {

    private static final Logger LOGGER = Logger.getLogger(SoapMapper.class.getName());

    private final SoapEnvelopeWrapper envelopeWrapper;
    private final JAXBContext jaxbContext;

    public SoapMapper(SoapEnvelopeWrapper envelopeWrapper, JAXBContext jaxbContext) {
        this.envelopeWrapper = envelopeWrapper;
        this.jaxbContext = jaxbContext;
    }

    public String buildEnvelope(FileUploadRequest request, SoapProperties props, String traceId) {
        try {
            SoapRequestHeader headerModel = new SoapRequestHeader();
            headerModel.setSystemId(props.systemId());
            headerModel.setMessageId(traceId);
            headerModel.setTimestamp(Instant.now().toString());
            if (props.userName() != null && !props.userName().isBlank()) {
                headerModel.setUserId(new SoapUserId(props.userName(), props.userToken()));
            }
            if (!props.messageContext().isEmpty()) {
                headerModel.setMessageContext(new SoapMessageContext(
                    props.messageContext().entrySet().stream()
                        .map(e -> new SoapMessageProperty(e.getKey(), e.getValue()))
                        .toList()
                ));
            }
            if (props.destinationName() != null && !props.destinationName().isBlank()) {
                headerModel.setDestination(new SoapDestination(
                    props.destinationName(), props.destinationNamespace(), props.destinationOperation()));
            }
            if (!props.classifications().isEmpty()) {
                headerModel.setClassifications(new SoapClassifications(props.classifications()));
            }

            String base64Content = request.getContent() != null ? Base64.getEncoder().encodeToString(request.getContent()) : "";
            String safeFilename = request.getFilename() != null ? request.getFilename() : "unknown";
            MetaDataWrapper metaDataWrapper = null;
            if (!props.metaData().isEmpty()) {
                metaDataWrapper = new MetaDataWrapper(
                    props.metaData().entrySet().stream()
                        .map(e -> new MetaDataEntry(e.getKey(), e.getValue()))
                        .toList()
                );
            }
            TransmitirDocumentoRequest bodyModel = new TransmitirDocumentoRequest(
                request.getSubTipoDocumental(),
                safeFilename,
                base64Content,
                metaDataWrapper
            );

            SoapHeader soapHeader = new SoapHeader(headerModel);
            SoapBody soapBody = new SoapBody(bodyModel);
            SoapEnvelope envelope = new SoapEnvelope(soapHeader, soapBody);

            StringWriter sw = new StringWriter();
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            marshaller.marshal(envelope, sw);
            return sw.toString();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error building SOAP envelope for traceId=" + traceId, e);
            throw ProcessingException.withTraceId(
                "Failed to build SOAP envelope: " + e.getMessage(), ProcessingResultCodes.UNKNOWN_ERROR.name(), traceId, e);
        }
    }

    public ExternalServiceResponse parseResponse(String xml, String traceId) {
        try {
            TransmitirDocumentoResponse response = envelopeWrapper.unwrapResponse(xml, TransmitirDocumentoResponse.class);
            Instant processedAt;
            try {
                processedAt = response.getProcessedAt() != null ? Instant.parse(response.getProcessedAt()) : Instant.now();
            } catch (DateTimeParseException e) {
                LOGGER.log(Level.WARNING, "TraceId {0}: Failed to parse processedAt date [{1}]. Using current time.", new Object[]{traceId, response.getProcessedAt()});
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
            LOGGER.log(Level.SEVERE, "Error parsing SOAP V2 response for traceId=" + traceId, e);
            throw ProcessingException.withTraceId("Failed to parse SOAP V2 response: " + e.getMessage(), ProcessingResultCodes.INVALID_RESPONSE.name(), traceId, e);
        }
    }
}
