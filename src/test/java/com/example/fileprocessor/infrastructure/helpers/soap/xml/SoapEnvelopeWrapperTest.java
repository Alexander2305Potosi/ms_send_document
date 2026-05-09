package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapEnvelopeWrapperTest {

    private SoapEnvelopeWrapper wrapper;

    @BeforeEach
    void setUp() throws Exception {
        JAXBContext context = JAXBContext.newInstance(TransmitirDocumentoResponse.class);
        wrapper = new SoapEnvelopeWrapper(context);
    }

    @Test
    void unwrapResponse_withValidXml_returnsResponseObject() {
        // Usamos el nombre de elemento sin namespace para que coincida con @XmlRootElement de la clase
        String validXml = 
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "  <soapenv:Body>" +
            "    <transmitirDocumentoResponse>" +
            "      <status>SUCCESS</status>" +
            "      <message>OK</message>" +
            "      <correlationId>123</correlationId>" +
            "    </transmitirDocumentoResponse>" +
            "  </soapenv:Body>" +
            "</soapenv:Envelope>";

        TransmitirDocumentoResponse response = wrapper.unwrapResponse(validXml, TransmitirDocumentoResponse.class);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("123", response.getCorrelationId());
    }

    @Test
    void unwrapResponse_withSoapFault_throwsProcessingException() {
        String faultXml = 
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "  <soapenv:Body>" +
            "    <soapenv:Fault>" +
            "      <faultcode>soapenv:Client</faultcode>" +
            "      <faultstring>Invalid request</faultstring>" +
            "    </soapenv:Fault>" +
            "  </soapenv:Body>" +
            "</soapenv:Envelope>";

        ProcessingException exception = assertThrows(ProcessingException.class, 
            () -> wrapper.unwrapResponse(faultXml, TransmitirDocumentoResponse.class));
        
        assertTrue(exception.getMessage().contains("Invalid request"));
    }
}
