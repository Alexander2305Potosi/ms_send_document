package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
@XmlRootElement(name = "soapenv:Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapEnvelope {

    @XmlElement(name = "soapenv:Header", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
    private SoapHeader header;

    @XmlElement(name = "soapenv:Body", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
    private SoapBody body;

    public SoapEnvelope() {}

    public SoapEnvelope(SoapHeader header, SoapBody body) {
        this.header = header;
        this.body = body;
    }

    public SoapHeader getHeader() { return header; }
    public void setHeader(SoapHeader header) { this.header = header; }

    public SoapBody getBody() { return body; }
    public void setBody(SoapBody body) { this.body = body; }
}
