package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "requestHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapV2RequestHeader {

    @XmlElement(name = "systemId")
    private String systemId;

    @XmlElement(name = "messageId")
    private String messageId;

    @XmlElement(name = "timestamp")
    private String timestamp;

    @XmlElement(name = "messageContext")
    private SoapV2MessageContext messageContext;

    @XmlElement(name = "userId")
    private SoapV2UserId userId;

    @XmlElement(name = "destination")
    private SoapV2Destination destination;

    @XmlElement(name = "classifications")
    private SoapV2Classifications classifications;

    public SoapV2RequestHeader() {}

    public void setSystemId(String systemId) { this.systemId = systemId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setMessageContext(SoapV2MessageContext messageContext) { this.messageContext = messageContext; }
    public void setUserId(SoapV2UserId userId) { this.userId = userId; }
    public void setDestination(SoapV2Destination destination) { this.destination = destination; }
    public void setClassifications(SoapV2Classifications classifications) { this.classifications = classifications; }

    public String getSystemId() { return systemId; }
    public String getMessageId() { return messageId; }
    public String getTimestamp() { return timestamp; }
    public SoapV2MessageContext getMessageContext() { return messageContext; }
    public SoapV2UserId getUserId() { return userId; }
    public SoapV2Destination getDestination() { return destination; }
    public SoapV2Classifications getClassifications() { return classifications; }
}
