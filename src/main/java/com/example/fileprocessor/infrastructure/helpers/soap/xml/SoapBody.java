package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoRequest;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapBody {

    @XmlElement(name = SoapConstants.EL_TRANSMITIR_DOCUMENTO_REQUEST, namespace = SoapConstants.NS_V1)
    private TransmitirDocumentoRequest transmitirDocumentoRequest;

    public SoapBody() {}

    public SoapBody(TransmitirDocumentoRequest transmitirDocumentoRequest) {
        this.transmitirDocumentoRequest = transmitirDocumentoRequest;
    }

    public TransmitirDocumentoRequest getTransmitirDocumentoRequest() { return transmitirDocumentoRequest; }
    public void setTransmitirDocumentoRequest(TransmitirDocumentoRequest transmitirDocumentoRequest) { this.transmitirDocumentoRequest = transmitirDocumentoRequest; }
}
