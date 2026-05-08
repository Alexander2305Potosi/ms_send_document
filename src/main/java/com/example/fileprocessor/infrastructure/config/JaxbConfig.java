package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.MetaDataEntry;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.MetaDataWrapper;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.TransmitirDocumentoRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body.TransmitirDocumentoResponse;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2Classifications;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2Destination;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2MessageContext;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2MessageProperty;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2RequestHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.header.SoapV2UserId;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized JAXB configuration.
 *
 * <p>{@link JAXBContext} initialization is expensive (class-level reflection).
 * Declaring it as a singleton {@code @Bean} ensures it is created exactly once
 * at application startup and shared across all components that need it
 * ({@code SoapV2Mapper}, {@code SoapEnvelopeWrapper}).
 */
@Configuration
public class JaxbConfig {

    /**
     * Shared {@link JAXBContext} covering all SOAP V1 and V2 model classes.
     *
     * <p>Any new JAXB-annotated model class must be added here so that
     * the context can discover it at startup rather than failing at runtime.
     */
    @Bean
    public JAXBContext soapJaxbContext() throws JAXBException {
        return JAXBContext.newInstance(
                // ── SOAP V2 body ───────────────────────────────────────────
                TransmitirDocumentoRequest.class,
                TransmitirDocumentoResponse.class,
                MetaDataWrapper.class,
                MetaDataEntry.class,
                // ── SOAP V2 header ─────────────────────────────────────────
                SoapV2RequestHeader.class,
                SoapV2MessageContext.class,
                SoapV2MessageProperty.class,
                SoapV2UserId.class,
                SoapV2Destination.class,
                SoapV2Classifications.class,
                // ── SOAP V1 ────────────────────────────────────────────────
                UploadFileRequest.class,
                UploadFileResponse.class
        );
    }
}
