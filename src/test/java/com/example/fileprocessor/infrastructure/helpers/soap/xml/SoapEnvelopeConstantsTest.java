package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapEnvelopeConstantsTest {

    @Test
    void constants_shouldNotBeEmpty() {
        assertNotNull(SoapEnvelopeConstants.MSG_SOAP_BODY_NOT_FOUND);
        assertNotNull(SoapEnvelopeConstants.MSG_RESPONSE_ELEMENT_NOT_FOUND);
        assertNotNull(SoapEnvelopeConstants.MSG_PARSE_ERROR);
    }

    @Test
    void constants_shouldContainDescriptiveText() {
        assertTrue(SoapEnvelopeConstants.MSG_SOAP_BODY_NOT_FOUND.contains("Body"));
        assertTrue(SoapEnvelopeConstants.MSG_RESPONSE_ELEMENT_NOT_FOUND.contains("Response"));
        assertTrue(SoapEnvelopeConstants.MSG_PARSE_ERROR.contains("parse"));
    }
}