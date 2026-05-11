package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
public class SoapHeader {

    @XmlAnyElement(lax = true)
    private Object any;

    public SoapHeader(Object any) {
        this.any = any;
    }
}
