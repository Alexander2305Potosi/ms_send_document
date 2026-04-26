package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.usecase.DocumentErrorCodes;
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

@Component
public class SoapMapper {

    private static final Logger log = LoggerFactory.getLogger(SoapMapper.class);

    private static final String SOAP_HEADER =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<soap:Envelope xmlns:soap=\"" + SoapNamespaces.SOAP_ENVELOPE + "\"\n" +
        "               xmlns:file=\"" + SoapNamespaces.FILE_SERVICE + "\">\n" +
        "  <soap:Header/>\n" +
        "  <soap:Body>\n";
    private static final String SOAP_FOOTER =
        "  </soap:Body>\n" +
        "</soap:Envelope>\n";

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

    public String toSoapXml(SoapRequest request) {
        log.debug("Converting SoapRequest to XML body for traceId: {}", request.getTraceId());

        UploadFileRequest uploadRequest = new UploadFileRequest(
            request.getFileContentBase64(),
            request.getFilename(),
            request.getContentType(),
            request.getFileSize(),
            request.getTraceId(),
            request.getTimestamp().toString(),
            request.getParentFolder(),
            request.getChildFolder()
        );

        return marshalRequest(uploadRequest);
    }

    public String toFullSoapMessage(SoapRequest request) {
        log.debug("Generating full SOAP message for traceId: {}", request.getTraceId());

        String soapBody = toSoapXml(request);
        return SOAP_HEADER + soapBody + SOAP_FOOTER;
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
            throw new SoapCommunicationException("Failed to marshal SOAP request", DocumentErrorCodes.UNKNOWN_ERROR, null, e);
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
                DocumentErrorCodes.INVALID_RESPONSE, traceId);
        }
    }
}