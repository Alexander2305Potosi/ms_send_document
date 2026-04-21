package com.example.fileprocessor.infrastructure.soap.xml.model;

import com.example.fileprocessor.infrastructure.soap.xml.SoapNamespaces;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "UploadFileResponse", namespace = SoapNamespaces.FILE_SERVICE)
@XmlAccessorType(XmlAccessType.FIELD)
public class UploadFileResponse {

    @XmlElement(name = "status", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String status;

    @XmlElement(name = "message", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String message;

    @XmlElement(name = "correlationId", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String correlationId;

    @XmlElement(name = "processedAt", namespace = SoapNamespaces.FILE_SERVICE)
    private String processedAt;

    @XmlElement(name = "externalReference", namespace = SoapNamespaces.FILE_SERVICE)
    private String externalReference;

    public UploadFileResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(String processedAt) {
        this.processedAt = processedAt;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }
}
