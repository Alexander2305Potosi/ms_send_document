package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapRequestHeader;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapHeader {

    @XmlElement(name = "hdr:RequestHeader", namespace = "http://example.com/header") // Default namespace, will be overridden by props
    private SoapRequestHeader requestHeader;

    public SoapHeader() {}

    public SoapHeader(SoapRequestHeader requestHeader) {
        this.requestHeader = requestHeader;
    }

    public SoapRequestHeader getRequestHeader() { return requestHeader; }
    public void setRequestHeader(SoapRequestHeader requestHeader) { this.requestHeader = requestHeader; }
}
