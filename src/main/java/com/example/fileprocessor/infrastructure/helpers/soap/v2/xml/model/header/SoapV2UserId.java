package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapV2UserId {

    @XmlElement(name = "userName")
    private String userName;

    @XmlElement(name = "userToken")
    private String userToken;

    public SoapV2UserId() {}

    public SoapV2UserId(String userName, String userToken) {
        this.userName = userName;
        this.userToken = userToken;
    }

    public String getUserName() { return userName; }
    public String getUserToken() { return userToken; }

    public void setUserName(String userName) { this.userName = userName; }
    public void setUserToken(String userToken) { this.userToken = userToken; }
}
