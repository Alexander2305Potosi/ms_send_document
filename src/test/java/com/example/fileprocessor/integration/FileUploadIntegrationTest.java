package com.example.fileprocessor.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.IOException;
import java.time.Instant;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FileUploadIntegrationTest {

    static MockWebServer mockWebServer;

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            mockWebServer = new MockWebServer();
            mockWebServer.start(8888);
            registry.add("app.soap.endpoint", () -> mockWebServer.url("/soap/fileservice").toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to start mock server", e);
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private String createSuccessResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:file=\"http://example.com/fileservice\">" +
            "<soap:Header/>" +
            "<soap:Body>" +
            "<file:UploadFileResponse>" +
            "<file:status>SUCCESS</file:status>" +
            "<file:message>File uploaded successfully</file:message>" +
            "<file:correlationId>int-123-456</file:correlationId>" +
            "<file:processedAt>" + Instant.now().toString() + "</file:processedAt>" +
            "<file:externalReference>ext-ref-123</file:externalReference>" +
            "</file:UploadFileResponse>" +
            "</soap:Body>" +
            "</soap:Envelope>";
    }

    private String createError500Response() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "<soap:Header/>" +
            "<soap:Body>" +
            "<soap:Fault>" +
            "<faultcode>soap:Server</faultcode>" +
            "<faultstring>Internal Server Error</faultstring>" +
            "</soap:Fault>" +
            "</soap:Body>" +
            "</soap:Envelope>";
    }

    @Test
    void uploadFile_shouldReturnSuccess_whenSoapReturnsSuccess() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(createSuccessResponse())
            .addHeader("Content-Type", "text/xml"));

        MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDisposition(ContentDisposition.builder("form-data")
            .name("file")
            .filename("test.pdf")
            .build());
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);

        byte[] fileContent = "test pdf content".getBytes();
        parts.add("file", new HttpEntity<>(fileContent, fileHeaders));

        webTestClient.post()
            .uri("/api/v1/files")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(parts))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("SUCCESS")
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.correlationId").isEqualTo("int-123-456");
    }

    @Test
    void uploadFile_shouldReturnBadGateway_whenSoapReturns500() {
        // Configure mock server to return 500
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody(createError500Response())
            .addHeader("Content-Type", "text/xml"));

        MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDisposition(ContentDisposition.builder("form-data")
            .name("file")
            .filename("test.pdf")
            .build());
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);

        byte[] fileContent = "test pdf content".getBytes();
        parts.add("file", new HttpEntity<>(fileContent, fileHeaders));

        webTestClient.post()
            .uri("/api/v1/files")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(parts))
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    void uploadFile_shouldReturnBadRequest_whenInvalidFileType() {
        // No need mock server for this test - validation happens before SOAP call

        MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDisposition(ContentDisposition.builder("form-data")
            .name("file")
            .filename("test.exe")
            .build());
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        byte[] fileContent = "exe content".getBytes();
        parts.add("file", new HttpEntity<>(fileContent, fileHeaders));

        webTestClient.post()
            .uri("/api/v1/files")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(parts))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("INVALID_FILE_TYPE");
    }
}
