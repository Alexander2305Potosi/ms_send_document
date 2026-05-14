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
public class SoapBody {

    @XmlAnyElement(lax = true)
    private Object any;

    public SoapBody(Object any) {
        this.any = any;
    }
}
