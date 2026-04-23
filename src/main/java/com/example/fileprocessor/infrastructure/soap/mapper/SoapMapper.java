package com.example.fileprocessor.infrastructure.soap.mapper;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.soap.xml.SoapNamespaces;
import com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileResponse;
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

    public SoapMapper(SoapEnvelopeWrapper envelopeWrapper, JAXBContext jaxbContext) {
        this.envelopeWrapper = envelopeWrapper;
        this.jaxbContext = jaxbContext;
    }

    public String toSoapXml(SoapRequest request) {
        log.debug("Converting SoapRequest to XML body for traceId: {}", request.getTraceId());

        UploadFileRequest uploadRequest = new UploadFileRequest(
            request.getFileContentBase64(),
            request.getFilename(),
            request.getContentType(),
            request.getFileSize(),
            request.getTraceId(),
            request.getTimestamp().toString()
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
            throw new SoapCommunicationException("Failed to marshal SOAP request", "MARSHALL_ERROR", null, e);
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
                .status(response.getStatus() != null ? response.getStatus() : "UNKNOWN")
                .message(response.getMessage() != null ? response.getMessage() : "No message received")
                .correlationId(response.getCorrelationId() != null ? response.getCorrelationId() : "N/A")
                .traceId(traceId)
                .processedAt(processedAt)
                .externalReference(response.getExternalReference())
                .build();
        } catch (Exception e) {
            log.error("Error parsing SOAP response: {}", e.getMessage());
            throw new SoapCommunicationException(
                "Failed to parse SOAP response: " + e.getMessage(),
                "INVALID_RESPONSE", traceId);
        }
    }
}