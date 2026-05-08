package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapMessageProperty {

    @XmlElement(name = "key")
    private String key;

    @XmlElement(name = "value")
    private String value;

    public SoapMessageProperty() {}

    public SoapMessageProperty(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
