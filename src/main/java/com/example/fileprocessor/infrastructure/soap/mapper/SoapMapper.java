package com.example.fileprocessor.infrastructure.soap.mapper;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.infrastructure.soap.xml.SoapEnvelopeWrapper;
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
    private final SoapEnvelopeWrapper envelopeWrapper;
    private final JAXBContext jaxbContext;

    public SoapMapper(SoapEnvelopeWrapper envelopeWrapper) throws JAXBException {
        this.envelopeWrapper = envelopeWrapper;
        this.jaxbContext = JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);
    }

    public String toSoapXml(SoapRequest request) {
        log.debug("Converting SoapRequest to XML body for traceId: {}", request.traceId());

        UploadFileRequest uploadRequest = new UploadFileRequest(
            request.fileContentBase64(),
            request.filename(),
            request.contentType(),
            request.fileSize(),
            request.traceId(),
            request.timestamp().toString()
        );

        return marshalRequest(uploadRequest);
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
            throw new RuntimeException("Failed to marshal SOAP request", e);
        }
    }

    public SoapResponse fromSoapXml(String xml, String traceId) {
        log.debug("Parsing SOAP response for traceId: {}", traceId);

        try {
            UploadFileResponse response = envelopeWrapper.unwrapResponse(xml, UploadFileResponse.class);

            Instant processedAt = response.getProcessedAt() != null
                ? Instant.parse(response.getProcessedAt())
                : Instant.now();

            return new SoapResponse(
                response.getStatus() != null ? response.getStatus() : "UNKNOWN",
                response.getMessage() != null ? response.getMessage() : "No message received",
                response.getCorrelationId() != null ? response.getCorrelationId() : "N/A",
                traceId,
                processedAt,
                response.getExternalReference()
            );
        } catch (Exception e) {
            log.error("Error parsing SOAP response: {}", e.getMessage());
            return new SoapResponse(
                "ERROR",
                "Failed to parse SOAP response: " + e.getMessage(),
                "N/A",
                traceId,
                Instant.now(),
                null
            );
        }
    }
}
