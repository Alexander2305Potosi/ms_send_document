package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapV2Destination {

    @XmlElement(name = "name")
    private String name;

    @XmlElement(name = "namespace")
    private String namespace;

    @XmlElement(name = "operation")
    private String operation;

    public SoapV2Destination() {}

    public SoapV2Destination(String name, String namespace, String operation) {
        this.name = name;
        this.namespace = namespace;
        this.operation = operation;
    }

    public String getName() { return name; }
    public String getNamespace() { return namespace; }
    public String getOperation() { return operation; }

    public void setName(String name) { this.name = name; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public void setOperation(String operation) { this.operation = operation; }
}
