@XmlSchema(
    namespace = SoapConstants.NS_SOAPENV,
    elementFormDefault = XmlNsForm.QUALIFIED,
    xmlns = {
        @XmlNs(prefix = SoapConstants.PREFIX_SOAPENV, namespaceURI = SoapConstants.NS_SOAPENV)
    }
)
package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
