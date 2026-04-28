package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapNamespaces;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.Instant;
import java.util.Base64;

@Component
public class SoapMapper {

    private static final Logger log = LoggerFactory.getLogger(SoapMapper.class);

    private final SoapEnvelopeWrapper envelopeWrapper;
    private final JAXBContext jaxbContext;

    public SoapMapper(SoapEnvelopeWrapper envelopeWrapper) {
        this.envelopeWrapper = envelopeWrapper;
        try {
            this.jaxbContext = JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXB context", e);
        }
    }

    public String toSoapXml(DocumentSendRequest request) {
        log.debug("Converting DocumentSendRequest to XML body for traceId: {}", request.getTraceId());

        // FIX #5: Base64 encoding happens HERE in infrastructure, not in domain
        String base64Content = request.getFileContent() != null
            ? Base64.getEncoder().encodeToString(request.getFileContent())
            : "";

        UploadFileRequest uploadRequest = new UploadFileRequest(
            base64Content,  // Now properly encoded here in infrastructure
            request.getFilename(),
            request.getContentType(),
            request.getFileSize(),
            request.getTraceId(),
            Instant.now().toString(),  // Timestamp generated in infrastructure
            request.getParentFolder(),
            request.getChildFolder()
        );

        return marshalRequest(uploadRequest);
    }

    public String toFullSoapMessage(DocumentSendRequest request) {
        log.debug("Generating full SOAP message for traceId: {}", request.getTraceId());

        String soapBody = toSoapXml(request);
        return ApiConstants.SOAP_HEADER_PREFIX
            + ApiConstants.SOAP_HEADER_ENVELOPE_START + SoapNamespaces.SOAP_ENVELOPE + "\"\n"
            + "               xmlns:file=\"" + SoapNamespaces.FILE_SERVICE + "\">\n"
            + ApiConstants.SOAP_HEADER_ENVELOPE_END
            + soapBody
            + ApiConstants.SOAP_FOOTER_ENVELOPE_END;
    }

    private String marshalRequest(UploadFileRequest request) {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);

            StringWriter writer = new StringWriter();
            marshaller.marshal(request, writer);
            return writer.toString();
        } catch (JAXBException e) {
            log.error("Error marshalling SOAP request: {}", e.getMessage());
            throw new SoapCommunicationException("Failed to marshal SOAP request", ProcessingResultCodes.UNKNOWN_ERROR, null, e);
        }
    }

    public SoapResponse fromSoapXml(String xml, String traceId) {
        log.debug("Parsing SOAP response for traceId: {}", traceId);

        try {
            UploadFileResponse response = envelopeWrapper.unwrapResponse(xml, UploadFileResponse.class);

            Instant processedAt = response.getProcessedAt() != null
                ? Instant.parse(response.getProcessedAt())
                : Instant.now();

            return SoapResponse.builder()
                .status(response.getStatus() != null ? response.getStatus() : SoapResponseDefaults.UNKNOWN)
                .message(response.getMessage() != null ? response.getMessage() : SoapResponseDefaults.NO_MESSAGE)
                .correlationId(response.getCorrelationId() != null ? response.getCorrelationId() : SoapResponseDefaults.NOT_AVAILABLE)
                .traceId(traceId)
                .processedAt(processedAt)
                .externalReference(response.getExternalReference())
                .build();
        } catch (Exception e) {
            log.error("Error parsing SOAP response: {}", e.getMessage());
            throw new SoapCommunicationException(
                "Failed to parse SOAP response: " + e.getMessage(),
                ProcessingResultCodes.INVALID_RESPONSE, traceId);
        }
    }
}