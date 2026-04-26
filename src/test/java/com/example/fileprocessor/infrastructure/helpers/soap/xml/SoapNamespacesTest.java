package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapNamespacesTest {

    @Test
    void constants_shouldHaveCorrectValues() {
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", SoapNamespaces.SOAP_ENVELOPE);
        assertEquals("http://example.com/fileservice", SoapNamespaces.FILE_SERVICE);
        assertEquals("/UploadFile", SoapNamespaces.SOAP_ACTION_UPLOAD);
    }

    @Test
    void namespaces_shouldNotBeEmpty() {
        assertNotNull(SoapNamespaces.SOAP_ENVELOPE);
        assertNotNull(SoapNamespaces.FILE_SERVICE);
        assertNotNull(SoapNamespaces.SOAP_ACTION_UPLOAD);
    }

    @Test
    void SOAP_ACTION_UPLOAD_shouldStartWithSlash() {
        assertTrue(SoapNamespaces.SOAP_ACTION_UPLOAD.startsWith("/"));
    }
}