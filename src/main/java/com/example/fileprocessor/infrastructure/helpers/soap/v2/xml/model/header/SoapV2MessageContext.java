package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapV2MessageContext {

    @XmlElement(name = "property")
    private List<SoapV2MessageProperty> properties;

    public SoapV2MessageContext() {}

    public SoapV2MessageContext(List<SoapV2MessageProperty> properties) {
        this.properties = properties;
    }

    public List<SoapV2MessageProperty> getProperties() { return properties; }
}
