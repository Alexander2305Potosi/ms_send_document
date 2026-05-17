package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ProductRestGatewayAdapterTest {

    private MockWebServer mockWebServer;
    private ProductRestGatewayAdapter adapter;

    private static final String PROD_ID = "prod-1";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        DocumentRestProperties properties = new DocumentRestProperties(
            "http://localhost:" + mockWebServer.getPort(),
            "/products",
            "/products/{productId}/documents",
            30
        );

        adapter = new ProductRestGatewayAdapter(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getDocument_returnsMappedProductDocumentFile() {
        String responseJson = """
            {"documentId":"doc-1","filename":"test.pdf","content":"VGVzdENvbnRlbnQ=","contentType":"application/pdf","size":12,"isZip":false,"originFolder":"origin","originCountry":"AR"}""";

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        StepVerifier.create(adapter.getDocument("prod-1", "doc-1")
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(file -> {
                assertEquals("prod-1", file.getProductId());
                assertEquals("doc-1", file.getDocumentId());
                assertEquals("test.pdf", file.getFilename());
                assertNotNull(file.getContent());
                assertEquals("origin", file.getOriginFolder());
                assertEquals("AR", file.getOriginCountry());
            })
            .verifyComplete();
    }

    @Test
    void getDocumentsByProduct_returnsMappedDocuments() {
        String responseJson = """
            [{"documentId":"doc-1","filename":"test.pdf","content":"VGVzdENvbnRlbnQ=","contentType":"application/pdf","size":12,"isZip":false,"originFolder":"origin","originCountry":"AR"}]""";

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        ProductMaestro product = ProductMaestro.builder().productId("prod-1").name("Product").build();

        StepVerifier.create(adapter.getDocumentsByProduct(product)
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(doc -> {
                assertEquals("prod-1", doc.getProductId());
                assertEquals("doc-1", doc.getDocumentId());
                assertEquals("test.pdf", doc.getName());
            })
            .verifyComplete();
    }
}
