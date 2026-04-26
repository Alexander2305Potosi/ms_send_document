package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapMapperConstantsTest {

    @Test
    void constants_shouldNotBeEmpty() {
        assertNotNull(SoapMapperConstants.SOAP_HEADER_PREFIX);
        assertNotNull(SoapMapperConstants.SOAP_HEADER_ENVELOPE_START);
        assertNotNull(SoapMapperConstants.SOAP_HEADER_ENVELOPE_END);
        assertNotNull(SoapMapperConstants.SOAP_FOOTER_ENVELOPE_END);
    }

    @Test
    void SOAP_HEADER_PREFIX_shouldContainXmlDeclaration() {
        assertTrue(SoapMapperConstants.SOAP_HEADER_PREFIX.contains("<?xml"));
        assertTrue(SoapMapperConstants.SOAP_HEADER_PREFIX.contains("UTF-8"));
    }

    @Test
    void SOAP_HEADER_ENVELOPE_START_shouldContainSoapEnvelope() {
        assertTrue(SoapMapperConstants.SOAP_HEADER_ENVELOPE_START.contains("soap:Envelope"));
    }

    @Test
    void SOAP_FOOTER_ENVELOPE_END_shouldCloseEnvelope() {
        assertTrue(SoapMapperConstants.SOAP_FOOTER_ENVELOPE_END.contains("</soap:Envelope>"));
    }
}