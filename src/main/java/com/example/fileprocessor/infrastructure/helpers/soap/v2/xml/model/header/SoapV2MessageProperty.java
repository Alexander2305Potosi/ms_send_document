package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapV2MessageProperty {

    @XmlElement(name = "key")
    private String key;

    @XmlElement(name = "value")
    private String value;

    public SoapV2MessageProperty() {}

    public SoapV2MessageProperty(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
