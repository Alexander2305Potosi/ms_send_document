package com.example.fileprocessor.infrastructure.helpers.soap.mapper;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SoapMapperTest {

    private SoapMapper soapMapper;
    private JAXBContext jaxbContext;
    private SoapProperties props;
    private ResourceLoader resourceLoader;

    @BeforeEach
    void setUp() throws Exception {
        jaxbContext = JAXBContext.newInstance(
            SoapEnvelope.class,
            SoapHeader.class,
            SoapBody.class,
            TransmitirDocumentoResponse.class,
            SoapFaultDetail.class,
            com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataWrapper.class,
            com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body.MetaDataEntry.class
        );
        props = Mockito.mock(SoapProperties.class);
        resourceLoader = Mockito.mock(ResourceLoader.class);

        // Configuración básica de propiedades
        when(props.soapNamespace()).thenReturn("http://std");
        when(props.bodyNamespace()).thenReturn("http://body");
        when(props.headerNamespace()).thenReturn("http://header");
        when(props.systemId()).thenReturn("SYS-1");
        when(props.userName()).thenReturn("user");
        when(props.userToken()).thenReturn("pass");
        when(props.metaData()).thenReturn(Map.of());

        // Template mock
        String template = "TEMPLATE {{traceId}} {{filename}} {{base64Content}} {{subTipo}} {{METADATA_BLOCK}}";
        Resource res = new ByteArrayResource(template.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(res);

        soapMapper = new SoapMapper(jaxbContext, props, resourceLoader);
        soapMapper.init();
    }

    @Test
    @DisplayName("Debe construir el envelope correctamente")
    void buildEnvelope_withSimpleRequest_returnsXml() {
        FileUploadRequest request = FileUploadRequest.builder()
            .filename("test.pdf")
            .content("hello".getBytes())
            .originFolder("PORTAL")
            .build();

        String xml = soapMapper.buildEnvelope(request, props, "trace-123");

        assertNotNull(xml);
        assertTrue(xml.contains("trace-123"), "Debe contener el traceId");
        assertTrue(xml.contains("test.pdf"), "Debe contener el nombre del archivo");
        assertTrue(xml.contains("aGVsbG8="), "Debe contener el contenido en Base64");
    }

    @Test
    @DisplayName("Debe escapar caracteres especiales en el XML")
    void buildEnvelope_withSpecialChars_escapesThem() {
        FileUploadRequest request = FileUploadRequest.builder()
            .filename("test & demo < >.pdf")
            .content("".getBytes())
            .originFolder("OR'IGIN")
            .build();

        String xml = soapMapper.buildEnvelope(request, props, "trace-123");

        assertTrue(xml.contains("test &amp; demo &lt; &gt;.pdf"), "Debe escapar caracteres especiales en el nombre");
        assertTrue(xml.contains("OR&apos;IGIN"), "Debe escapar comillas");
    }

    @Test
    @DisplayName("Debe parsear una respuesta exitosa")
    void parseResponse_withSuccessXml_returnsResponse() {
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
               <S:Body>
                  <transmitirDocumentoResponse>
                     <status>OK</status>
                     <message>Procesado</message>
                     <correlationId>CORR-1</correlationId>
                     <processedAt>2023-10-27T10:00:00Z</processedAt>
                  </transmitirDocumentoResponse>
               </S:Body>
            </S:Envelope>
            """;

        FileUploadResponse response = soapMapper.parseResponse(xml, "trace-1");

        assertNotNull(response);
        assertTrue(response.isSuccess(), "Debe ser exitosa");
        assertEquals("CORR-1", response.getCorrelationId());
        assertEquals("Procesado", response.getMessage());
    }

    @Test
    @DisplayName("Prueba de Humo: Debe mappear correctamente el error SOAP real proporcionado")
    void smokeTestRealSoapFault() {
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/" xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
               <SOAP-ENV:Header/>
               <S:Body>
                  <S:Fault>
                     <faultcode>S:Server</faultcode>
                     <faultstring>Error de servidor</faultstring>
                     <detail>
                        <ns3:systemException xmlns:ns3="http://example/V1.0">
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

        FileUploadResponse response = soapMapper.parseResponse(xml, "smoke-test-id");

        assertEquals("FAILURE", response.getStatus());
        assertEquals("1421", response.getCorrelationId());
        assertEquals("El parámetro archivo es obligatorio.", response.getMessage());
        assertEquals(ProcessingResultCodes.SOAP_ERROR.name(), response.getSyncStatus());
    }

    @Test
    @DisplayName("Debe manejar un Fault sin detalle usando faultstring")
    void parseResponse_withFaultNoDetail_returnsStandardMessage() {
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
               <S:Body>
                  <S:Fault>
                     <faultcode>S:Client</faultcode>
                     <faultstring>Invalid Request</faultstring>
                  </S:Fault>
               </S:Body>
            </S:Envelope>
            """;

        FileUploadResponse response = soapMapper.parseResponse(xml, "trace-1");

        assertFalse(response.isSuccess());
        assertEquals("Invalid Request", response.getMessage());
        assertEquals(ProcessingResultCodes.SOAP_ERROR.name(), response.getCorrelationId());
    }

    @Test
    @DisplayName("Debe fallar si el XML está mal formado")
    void parseResponse_withMalformedXml_throwsException() {
        String xml = "<S:Envelope> BODY UNCLOSED";
        
        assertThrows(ProcessingException.class, () -> 
            soapMapper.parseResponse(xml, "trace-fail")
        );
    }

    @Test
    @DisplayName("Debe generar el bloque de metadatos correctamente")
    void buildEnvelope_withMetadata_generatesMetadataBlock() {
        when(props.metaData()).thenReturn(Map.of("KEY1", "VAL1", "KEY2", "VAL2"));
        FileUploadRequest request = FileUploadRequest.builder().filename("test.pdf").content("".getBytes()).build();

        String xml = soapMapper.buildEnvelope(request, props, "trace-meta");

        assertTrue(xml.contains("<tiposMetaData>"), "Debe contener etiquetas tiposMetaData");
        assertTrue(xml.contains("<nombre>KEY1</nombre>"), "Debe contener el nombre de la clave 1");
        assertTrue(xml.contains("<valor>VAL1</valor>"), "Debe contener el valor 1");
    }

    @Test
    @DisplayName("Debe mappear manualmente si JAXB falla pero el elemento es el correcto")
    void parseResponse_withManualMapping_returnsResponse() {
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
               <S:Body>
                  <transmitirDocumentoResponse>
                     <status>SUCCESS</status>
                     <message>Manual mapping works</message>
                     <correlationId>MANUAL-1</correlationId>
                     <externalReference>REF-1</externalReference>
                  </transmitirDocumentoResponse>
               </S:Body>
            </S:Envelope>
            """;

        // This triggers the manual mapping branch when JAXB might not be aware of namespaces
        FileUploadResponse response = soapMapper.parseResponse(xml, "trace-manual");

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Manual mapping works", response.getMessage());
        assertEquals("MANUAL-1", response.getCorrelationId());
        assertEquals("REF-1", response.getExternalReference());
    }

    @Test
    @DisplayName("Debe manejar respuestas con cuerpo inesperado")
    void parseResponse_withUnexpectedBody_throwsException() {
        String xml = """
            <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
               <S:Body>
                  <unexpectedElement>Oops</unexpectedElement>
               </S:Body>
            </S:Envelope>
            """;

        assertThrows(ProcessingException.class, () -> 
            soapMapper.parseResponse(xml, "trace-unexpected")
        );
    }

    @Test
    @DisplayName("Debe manejar errores de inicialización del template")
    void init_withInvalidResource_throwsRuntimeException() {
        when(resourceLoader.getResource(anyString())).thenReturn(null);
        SoapMapper mapper = new SoapMapper(jaxbContext, props, resourceLoader);
        
        assertThrows(RuntimeException.class, mapper::init);
    }
}
