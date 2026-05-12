package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "detail")
public class SoapFaultDetail {

    @XmlElement(name = "systemException")
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
