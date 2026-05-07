package com.example.fileprocessor.infrastructure.helpers.soap.v2.mapper;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.config.SoapV2Properties;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapEnvelopeWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SoapV2MapperTest {

    private SoapV2Mapper mapper;
    private SoapV2Properties minimalProps;

    @BeforeEach
    void setUp() {
        SoapEnvelopeWrapper wrapper = new SoapEnvelopeWrapper();
        mapper = new SoapV2Mapper(wrapper);
        minimalProps = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            null, null, null, null, null,
            null, null, null, 30, 0
        );
    }

    private static FileUploadRequest request(String filename, byte[] content) {
        return FileUploadRequest.builder()
            .documentId("doc-1")
            .filename(filename)
            .contentType("application/pdf")
            .content(content)
            .origin("test-origin")
            .subTipoDocumental("Facturas")
            .build();
    }

    @Test
    void buildEnvelope_minimalProperties_containsRequiredElements() {
        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), minimalProps, "trace-1");

        assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<soapenv:Envelope");
        assertThat(xml).contains("xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"");
        assertThat(xml).contains("xmlns:v2=\"http://prueba.com/ents/SOI/MessageFormat/V2.1\"");
        assertThat(xml).contains("xmlns:v1=\"http://prueba.com/intf/factory/adminDocs/V1.0\"");
        assertThat(xml).contains("<soapenv:Header>");
        assertThat(xml).contains("<soapenv:Body>");
        assertThat(xml).contains("</soapenv:Envelope>");
    }

    @Test
    void buildEnvelope_minimalProperties_containsHeaderFields() {
        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), minimalProps, "trace-1");

        assertThat(xml).contains("<v2:requestHeader>");
        assertThat(xml).contains("<v2:systemId>sys-123</v2:systemId>");
        assertThat(xml).contains("<v2:messageId>trace-1</v2:messageId>");
        assertThat(xml).contains("<v2:timestamp>");
        assertThat(xml).contains("<v2:userId>");
        assertThat(xml).contains("<v2:userName>test-user</v2:userName>");
    }

    @Test
    void buildEnvelope_minimalProperties_containsBodyFields() {
        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), minimalProps, "trace-1");

        assertThat(xml).contains("<transmitirDocumento>");
        assertThat(xml).contains("<subTipoDocumental>Facturas</subTipoDocumental>");
        assertThat(xml).contains("<nombreArchivo>doc.pdf</nombreArchivo>");
        assertThat(xml).contains("<archivo>AQID</archivo>"); // Base64 of {1,2,3}
    }

    @Test
    void buildEnvelope_minimalProperties_omitsOptionalFields() {
        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), minimalProps, "trace-1");

        assertThat(xml).doesNotContain("<v2:userToken>");
        assertThat(xml).doesNotContain("<v2:messageContext>");
        assertThat(xml).doesNotContain("<v2:destination>");
        assertThat(xml).doesNotContain("<v2:classifications>");
        assertThat(xml).doesNotContain("<metaData>");
    }

    @Test
    void buildEnvelope_withUserToken_includesToken() {
        SoapV2Properties props = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            "token-abc", null, null, null, null,
            null, null, null, 30, 0
        );

        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), props, "trace-2");

        assertThat(xml).contains("<v2:userToken>token-abc</v2:userToken>");
    }

    @Test
    void buildEnvelope_withMessageContext_includesProperties() {
        SoapV2Properties props = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            null, null, null, null, null,
            null, Map.of("key1", "val1", "key2", "val2"), null, 30, 0
        );

        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), props, "trace-3");

        assertThat(xml).contains("<v2:messageContext>");
        assertThat(xml).contains("<v2:property>");
        assertThat(xml).contains("<v2:key>key1</v2:key>");
        assertThat(xml).contains("<v2:value>val1</v2:value>");
        assertThat(xml).contains("<v2:key>key2</v2:key>");
        assertThat(xml).contains("<v2:value>val2</v2:value>");
    }

    @Test
    void buildEnvelope_withDestination_includesFields() {
        SoapV2Properties props = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            null, "bussinesdocs",
            "http://prueba.com/dest/ns", "sendOperation", null,
            null, null, null, 30, 0
        );

        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), props, "trace-4");

        assertThat(xml).contains("<v2:destination>");
        assertThat(xml).contains("<v2:name>bussinesdocs</v2:name>");
        assertThat(xml).contains("<v2:namespace>http://prueba.com/dest/ns</v2:namespace>");
        assertThat(xml).contains("<v2:operation>sendOperation</v2:operation>");
    }

    @Test
    void buildEnvelope_withClassifications_includesList() {
        SoapV2Properties props = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            null, null, null, null, null,
            List.of("classA", "classB"), null, null, 30, 0
        );

        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), props, "trace-5");

        assertThat(xml).contains("<v2:classifications>");
        assertThat(xml).contains("<v2:classification>classA</v2:classification>");
        assertThat(xml).contains("<v2:classification>classB</v2:classification>");
    }

    @Test
    void buildEnvelope_withMetaData_includesFields() {
        SoapV2Properties props = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            null, null, null, null, null,
            null, null, Map.of("idField", "00123"), 30, 0
        );

        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1, 2, 3}), props, "trace-6");

        assertThat(xml).contains("<metaData>");
        assertThat(xml).contains("<nombre>idField</nombre>");
        assertThat(xml).contains("<valor>00123</valor>");
    }

    @Test
    void buildEnvelope_nullContent_usesEmptyBase64() {
        FileUploadRequest req = request("doc.pdf", null);

        String xml = mapper.buildEnvelope(req, minimalProps, "trace-7");

        assertThat(xml).contains("<archivo></archivo>");
    }

    @Test
    void buildEnvelope_nullFilename_defaultsToUnknown() {
        FileUploadRequest req = request(null, new byte[]{1});

        String xml = mapper.buildEnvelope(req, minimalProps, "trace-8");

        assertThat(xml).contains("<nombreArchivo>unknown</nombreArchivo>");
    }

    @Test
    void buildEnvelope_blankToken_omitsToken() {
        SoapV2Properties props = new SoapV2Properties(
            "http://localhost:8080/soap/v2", "sys-123", "test-user",
            "http://prueba.com/ents/SOI/MessageFormat/V2.1",
            "http://prueba.com/intf/factory/adminDocs/V1.0",
            "   ", null, null, null, null,
            null, null, null, 30, 0
        );

        String xml = mapper.buildEnvelope(request("doc.pdf", new byte[]{1}), props, "trace-9");

        assertThat(xml).doesNotContain("<v2:userToken>");
    }

    @Test
    void parseResponse_validXml_returnsExternalServiceResponse() {
        String xml = """
            <?xml version="1.0"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body>
                <transmitirDocumentoResponse>
                  <status>OK</status>
                  <message>Uploaded successfully</message>
                  <correlationId>corr-456</correlationId>
                  <processedAt>2026-05-06T10:15:30.123Z</processedAt>
                  <externalReference>ext-ref-789</externalReference>
                </transmitirDocumentoResponse>
              </soapenv:Body>
            </soapenv:Envelope>
            """;

        ExternalServiceResponse response = mapper.parseResponse(xml, "trace-99");

        assertEquals("OK", response.getStatus());
        assertEquals("Uploaded successfully", response.getMessage());
        assertEquals("corr-456", response.getCorrelationId());
        assertEquals("ext-ref-789", response.getExternalReference());
        assertTrue(response.isSuccess());
    }

    @Test
    void parseResponse_invalidXml_throwsProcessingException() {
        assertThrows(ProcessingException.class,
            () -> mapper.parseResponse("<not-xml>", "trace-100"));
    }
}
