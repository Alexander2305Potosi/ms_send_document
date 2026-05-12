package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapBody;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelope;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapHeader;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.SoapFaultDetail;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.TransmitirDocumentoResponse;
import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

class SoapMapperTest {

    private SoapMapper soapMapper;
    private JAXBContext jaxbContext;

    @BeforeEach
    void setUp() throws Exception {
        jaxbContext = JAXBContext.newInstance(
            SoapEnvelope.class,
            SoapHeader.class,
            SoapBody.class,
            TransmitirDocumentoResponse.class,
            SoapFaultDetail.class
        );
        SoapProperties props = Mockito.mock(SoapProperties.class);
        ResourceLoader resourceLoader = Mockito.mock(ResourceLoader.class);
        
        soapMapper = new SoapMapper(jaxbContext, props, resourceLoader);
    }

    @Test
    @DisplayName("Prueba de Humo: Debe mappear correctamente el error SOAP real proporcionado")
    void smokeTestRealSoapFault() {
        // GIVEN: El XML exacto proporcionado por el usuario
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/" xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
               <SOAP-ENV:Header/>
               <S:Body>
                  <S:Fault xmlns:ns3="http://www.w3.org/2003/05/soap-envelope">
                     <faultcode>S:Server</faultcode>
                     <faultstring/>
                     <detail>
                        <ns3:systemException xmlns:ns2="http://example/MessageFormat/V2.1" xmlns:ns3="http://example/V1.0">
                           <genericException>
                              <code>1421</code>
                              <description>El parámetro archivo es obligatorio.</description>
                           </genericException>
                        </ns3:systemException>
                     </detail>
                  </S:Fault>
               </S:Body>
            </S:Envelope>
            """;

        // WHEN: Procesamos la respuesta
        ExternalServiceResponse response = soapMapper.parseResponse(xml, "smoke-test-id");

        // DIAGNÓSTICO
        System.out.println("DEBUG - Status: " + response.getStatus());
        System.out.println("DEBUG - Code: " + response.getCorrelationId());
        System.out.println("DEBUG - Message: " + response.getMessage());

        // THEN: Validamos que la información se haya extraído correctamente
        assertNotNull(response, "La respuesta no debe ser nula");
        assertEquals("FAILURE", response.getStatus(), "El estado debe ser FAILURE");
        assertEquals("1421", response.getCorrelationId(), "El código de error debe ser 1421");
        assertEquals("El parámetro archivo es obligatorio.", response.getMessage(), "El mensaje debe coincidir con el XML");
        assertNotNull(response.getProcessedAt(), "La fecha de procesamiento debe estar presente");
    }
}
