package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = SoapConstants.EL_ENVELOPE, namespace = SoapConstants.NS_SOAPENV)
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapEnvelope {

    @XmlElement(name = SoapConstants.EL_HEADER, namespace = SoapConstants.NS_SOAPENV)
    private SoapHeader header;

    @XmlElement(name = SoapConstants.EL_BODY, namespace = SoapConstants.NS_SOAPENV)
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
