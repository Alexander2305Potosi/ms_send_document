package com.example.fileprocessor.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(8888);

        System.setProperty("SOAP_ENDPOINT", mockWebServer.url("/soap/fileservice").toString());
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void uploadFile_shouldReturnSuccess_whenSoapReturnsSuccess() {
        String responseXml = "<?xml version=\"1.0\"?\u003e" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\u003e" +
            "<soap:Body\u003e" +
            "<response\u003e" +
            "<status\u003eSUCCESS</status\u003e" +
            "<message\u003eFile uploaded successfully</message\u003e" +
            "<correlationId\u003eint-123-456</correlationId\u003e" +
            "<processedAt\u003e" + Instant.now().toString() + "</processedAt\u003e" +
            "</response\u003e" +
            "</soap:Body\u003e" +
            "</soap:Envelope\u003e";

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(responseXml)
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
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("<?xml version=\"1.0\"?\u003e\u003csoap:Fault\u003eInternal Error\u003c/soap:Fault\u003e"));

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
            .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("BAD_GATEWAY");
    }

    @Test
    void uploadFile_shouldReturnBadRequest_whenInvalidFileType() {
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
