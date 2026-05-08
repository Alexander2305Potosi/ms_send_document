package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "requestHeader")
@XmlAccessorType(XmlAccessType.FIELD)
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

    public SoapRequestHeader() {}

    public void setSystemId(String systemId) { this.systemId = systemId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setMessageContext(SoapMessageContext messageContext) { this.messageContext = messageContext; }
    public void setUserId(SoapUserId userId) { this.userId = userId; }
    public void setDestination(SoapDestination destination) { this.destination = destination; }
    public void setClassifications(SoapClassifications classifications) { this.classifications = classifications; }

    public String getSystemId() { return systemId; }
    public String getMessageId() { return messageId; }
    public String getTimestamp() { return timestamp; }
    public SoapMessageContext getMessageContext() { return messageContext; }
    public SoapUserId getUserId() { return userId; }
    public SoapDestination getDestination() { return destination; }
    public SoapClassifications getClassifications() { return classifications; }
}
