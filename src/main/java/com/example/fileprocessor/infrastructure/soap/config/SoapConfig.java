package com.example.fileprocessor.infrastructure.soap.config;

import com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SoapConfig {

    @Bean
    public JAXBContext soapJaxbContext() {
        try {
            return JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXB context", e);
        }
    }
}
