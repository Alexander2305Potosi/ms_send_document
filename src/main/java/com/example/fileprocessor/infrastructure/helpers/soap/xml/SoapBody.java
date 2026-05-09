package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoRequest;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapBody {

    @XmlElement(name = "body:TransmitirDocumentoRequest", namespace = "http://example.com/body") // Default namespace, will be overridden by props
    private TransmitirDocumentoRequest transmitirDocumentoRequest;

    public SoapBody() {}

    public SoapBody(TransmitirDocumentoRequest transmitirDocumentoRequest) {
        this.transmitirDocumentoRequest = transmitirDocumentoRequest;
    }

    public TransmitirDocumentoRequest getTransmitirDocumentoRequest() { return transmitirDocumentoRequest; }
    public void setTransmitirDocumentoRequest(TransmitirDocumentoRequest transmitirDocumentoRequest) { this.transmitirDocumentoRequest = transmitirDocumentoRequest; }
}
