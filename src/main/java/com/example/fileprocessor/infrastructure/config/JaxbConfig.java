package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapBody;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.SoapFaultDetail;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized JAXB configuration for unified SOAP protocol.
 * Reduced to essential classes for response parsing and metadata generation.
 */
@Configuration
public class JaxbConfig {

    @Bean
    public JAXBContext soapJaxbContext() throws JAXBException {
        return JAXBContext.newInstance(
                SoapEnvelope.class,
                SoapHeader.class,
                SoapBody.class,
                TransmitirDocumentoResponse.class,
                SoapFaultDetail.class,
                MetaDataWrapper.class,
                MetaDataEntry.class
        );
    }
}
