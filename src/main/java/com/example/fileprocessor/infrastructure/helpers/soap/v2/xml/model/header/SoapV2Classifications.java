package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapV2Classifications {

    @XmlElement(name = "classification")
    private List<String> classifications;

    public SoapV2Classifications() {}

    public SoapV2Classifications(List<String> classifications) {
        this.classifications = classifications;
    }

    public List<String> getClassifications() { return classifications; }
}
