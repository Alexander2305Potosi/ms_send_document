package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapBody;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapClassifications;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapDestination;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapMessageContext;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapMessageProperty;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapRequestHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.header.SoapUserId;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized JAXB configuration for unified SOAP protocol.
 */
@Configuration
public class JaxbConfig {

    /**
     * Shared {@link JAXBContext} covering all unified SOAP model classes.
     */
    @Bean
    public JAXBContext soapJaxbContext() throws JAXBException {
        return JAXBContext.newInstance(
                SoapEnvelope.class,
                SoapHeader.class,
                SoapBody.class,
                TransmitirDocumentoRequest.class,
                TransmitirDocumentoResponse.class,
                MetaDataWrapper.class,
                MetaDataEntry.class,
                SoapRequestHeader.class,
                SoapMessageContext.class,
                SoapMessageProperty.class,
                SoapUserId.class,
                SoapDestination.class,
                SoapClassifications.class
        );
    }
}
