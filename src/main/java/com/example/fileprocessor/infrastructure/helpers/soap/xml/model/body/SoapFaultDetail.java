package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapFaultDetail {

    @XmlElement(name = "systemException", namespace = "http://prueba")
    private SystemException systemException;

    @Getter
    @Setter
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SystemException {
        @XmlElement(name = "genericException")
        private GenericException genericException;
    }

    @Getter
    @Setter
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class GenericException {
        @XmlElement(name = "code")
        private String code;
        @XmlElement(name = "description")
        private String description;
    }
}
