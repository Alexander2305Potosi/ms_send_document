package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapRequestHeader;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapHeader {

    @XmlElement(name = SoapConstants.EL_REQUEST_HEADER, namespace = SoapConstants.NS_V2)
    private SoapRequestHeader requestHeader;

    public SoapHeader() {}

    public SoapHeader(SoapRequestHeader requestHeader) {
        this.requestHeader = requestHeader;
    }

    public SoapRequestHeader getRequestHeader() { return requestHeader; }
    public void setRequestHeader(SoapRequestHeader requestHeader) { this.requestHeader = requestHeader; }
}
