package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SoapUserId {

    @XmlElement(name = "userName")
    private String userName;

    @XmlElement(name = "userToken")
    private String userToken;

    public SoapUserId() {}

    public SoapUserId(String userName, String userToken) {
        this.userName = userName;
        this.userToken = userToken;
    }

    public String getUserName() { return userName; }
    public String getUserToken() { return userToken; }

    public void setUserName(String userName) { this.userName = userName; }
    public void setUserToken(String userToken) { this.userToken = userToken; }
}
