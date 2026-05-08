package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapMessageContext {

    @XmlElement(name = "property")
    private List<SoapMessageProperty> properties;

    public SoapMessageContext() {}

    public SoapMessageContext(List<SoapMessageProperty> properties) {
        this.properties = properties;
    }

    public List<SoapMessageProperty> getProperties() { return properties; }
}
