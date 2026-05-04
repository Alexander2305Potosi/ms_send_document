package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.SoapConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapMapper {

    private static final Logger log = Logger.getLogger(SoapMapper.class.getName());

    private static final String DEFAULT_STATUS = "UNKNOWN";
    private static final String DEFAULT_MESSAGE = "No message received";
    private static final String DEFAULT_CORRELATION_ID = "N/A";

    private final SoapEnvelopeWrapper envelopeWrapper;
    private final JAXBContext jaxbContext;

    public SoapMapper(SoapEnvelopeWrapper envelopeWrapper) {
        this.envelopeWrapper = envelopeWrapper;
        this.jaxbContext = envelopeWrapper.getJaxbContext();
    }

    public String toSoapXml(FileUploadRequest fileUploadRequest) {
        String base64Content = fileUploadRequest.getContent() != null
            ? Base64.getEncoder().encodeToString(fileUploadRequest.getContent())
            : "";

        UploadFileRequest uploadRequest = new UploadFileRequest(
                base64Content,
                fileUploadRequest.getFilename(),
                fileUploadRequest.getContentType(),
                fileUploadRequest.getFileSize(),
                Instant.now().toString(),
                ".",
                "."
        );

        return marshalRequest(uploadRequest);
    }

    public String toFullSoapMessage(FileUploadRequest fileUploadRequest) {
        String soapBody = toSoapXml(fileUploadRequest);
        return SoapConstants.HEADER_PREFIX
            + SoapConstants.ENVELOPE_START + SoapConstants.SOAP_ENVELOPE + "\"\n"
            + "               xmlns:file=\"" + SoapConstants.FILE_SERVICE + "\">\n"
            + SoapConstants.ENVELOPE_END
            + soapBody
            + SoapConstants.FOOTER_ENVELOPE_END;
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
            log.log(Level.SEVERE, "Error marshalling SOAP request: {0}", new Object[]{e.getMessage()});
            throw ProcessingException.withTraceId("Failed to marshal SOAP request", ProcessingResultCodes.UNKNOWN_ERROR, "", e);
        }
    }

    public ExternalServiceResponse fromSoapXml(String xml) {
        try {
            UploadFileResponse response = envelopeWrapper.unwrapResponse(xml, UploadFileResponse.class);

            Instant processedAt = response.getProcessedAt() != null
                ? Instant.parse(response.getProcessedAt())
                : Instant.now();

            return ExternalServiceResponse.builder()
                .status(Objects.requireNonNullElse(response.getStatus(), DEFAULT_STATUS))
                .message(Objects.requireNonNullElse(response.getMessage(), DEFAULT_MESSAGE))
                .correlationId(Objects.requireNonNullElse(response.getCorrelationId(), DEFAULT_CORRELATION_ID))
                .processedAt(processedAt)
                .externalReference(response.getExternalReference())
                .build();
        } catch (Exception e) {
            if (e instanceof ProcessingException pe) throw pe;
            log.log(Level.SEVERE, "Error parsing SOAP response: {0}", new Object[]{e.getMessage()});
            throw ProcessingException.withTraceId(
                "Failed to parse SOAP response: " + e.getMessage(),
                ProcessingResultCodes.INVALID_RESPONSE, "", e);
        }
    }
}