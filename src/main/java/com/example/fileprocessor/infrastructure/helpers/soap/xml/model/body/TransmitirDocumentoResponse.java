package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "transmitirDocumentoResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransmitirDocumentoResponse {

    @XmlElement(name = "status")
    private String status;

    @XmlElement(name = "message")
    private String message;

    @XmlElement(name = "correlationId")
    private String correlationId;

    @XmlElement(name = "processedAt")
    private String processedAt;

    @XmlElement(name = "externalReference")
    private String externalReference;

    public TransmitirDocumentoResponse() {}

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getCorrelationId() { return correlationId; }
    public String getProcessedAt() { return processedAt; }
    public String getExternalReference() { return externalReference; }
}
