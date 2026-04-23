package com.example.fileprocessor.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

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

    @BeforeEach
    void setUp() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(createSuccessResponse())
            .addHeader("Content-Type", "text/xml"));
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
            "<file:processedAt>2024-01-15T10:30:00Z</file:processedAt>" +
            "<file:externalReference>ext-ref-123</file:externalReference>" +
            "</file:UploadFileResponse>" +
            "</soap:Body>" +
            "</soap:Envelope>";
    }

    @Test
    void getFile_shouldReturnAccepted_whenDocumentExists() {
        // The endpoint now returns 202 Accepted immediately and processes asynchronously
        webTestClient.get()
            .uri("/api/v1/files/doc-001")
            .exchange()
            .expectStatus().isAccepted()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PROCESSING")
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void getAllFiles_shouldReturnAccepted_whenCalled() {
        webTestClient.get()
            .uri("/api/v1/files")
            .exchange()
            .expectStatus().isAccepted()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PROCESSING")
            .jsonPath("$.success").isEqualTo(true);
    }
}
