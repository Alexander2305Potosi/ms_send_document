package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@XmlRootElement(name = "requestHeader")
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
public class SoapRequestHeader {

    @XmlElement(name = "systemId")
    private String systemId;

    @XmlElement(name = "messageId")
    private String messageId;

    @XmlElement(name = "timestamp")
    private String timestamp;

    @XmlElement(name = "messageContext")
    private SoapMessageContext messageContext;

    @XmlElement(name = "userId")
    private SoapUserId userId;

    @XmlElement(name = "destination")
    private SoapDestination destination;

    @XmlElement(name = "classifications")
    private SoapClassifications classifications;
}
