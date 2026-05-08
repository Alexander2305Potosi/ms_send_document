package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapClassifications {

    @XmlElement(name = "classification")
    private List<String> classifications;

    public SoapClassifications() {}

    public SoapClassifications(List<String> classifications) {
        this.classifications = classifications;
    }

    public List<String> getClassifications() { return classifications; }
}
