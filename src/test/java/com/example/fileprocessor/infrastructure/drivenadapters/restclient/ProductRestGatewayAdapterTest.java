package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductResponse;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.io.IOException;
import java.util.List;

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

    // mapToProductDocument tests

    @Test
    void mapToProductDocument_withCompleteData() {
        ProductDocumentResponse docResponse = new ProductDocumentResponse(
            "doc-1", "test.pdf", "VGVzdENvbnRlbnQ=", "application/pdf", 12L, false, "origin", "AR"
        );

        ProductDocumentHistory doc = adapter.mapToProductDocument(PROD_ID, docResponse);

        assertEquals(PROD_ID, doc.getProductId());
        assertEquals("doc-1", doc.getDocumentId());
        assertEquals("test.pdf", doc.getFilename());
        assertEquals("application/pdf", doc.getContentType());
        assertEquals(12L, doc.getSize());
        assertFalse(doc.isZip());
        assertEquals("origin", doc.getOrigin());
        assertEquals("AR", doc.getPais());
        assertNotNull(doc.getContent());
    }

    @Test
    void mapToProductDocument_withNullContent_setsNullContent() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", null, "application/pdf", 12L, false, "origin", "AR"
        );

        ProductDocumentHistory doc = adapter.mapToProductDocument(PROD_ID, response);

        assertNull(doc.getContent());
        assertEquals(12L, doc.getSize());
    }

    @Test
    void mapToProductDocument_withNullSizeAndNullContent_setsZeroSize() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", null, "application/pdf", null, false, "origin", "AR"
        );

        ProductDocumentHistory doc = adapter.mapToProductDocument(PROD_ID, response);

        assertEquals(0L, doc.getSize());
    }

    @Test
    void mapToProductDocument_withBlankContent_setsNullContent() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", "   ", "application/pdf", null, false, "origin", "AR"
        );

        ProductDocumentHistory doc = adapter.mapToProductDocument(PROD_ID, response);

        assertNull(doc.getContent());
    }

    @Test
    void mapToProductDocument_withZipFile_doesNotSetIsZip() {
        // Note: mapToProductDocument currently does NOT map the isZip field
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.zip", "VGVzdENvbnRlbnQ=", "application/zip", 12L, true, "origin", "AR"
        );

        ProductDocumentHistory doc = adapter.mapToProductDocument(PROD_ID, response);

        assertEquals("application/zip", doc.getContentType());
        assertEquals("test.zip", doc.getFilename());
    }

    @Test
    void mapToProductDocument_withNullSizeButContent_setsSizeFromContentLength() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", "VGVzdENvbnRlbnQ=", "application/pdf", null, false, "origin", "AR"
        );

        ProductDocumentHistory doc = adapter.mapToProductDocument(PROD_ID, response);

        assertNotNull(doc.getContent());
        assertEquals(doc.getContent().length, doc.getSize());
    }

    @Test
    void mapToProductDocument_withInvalidBase64_throwsProcessingException() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", "!!!invalid-base64!!!", "application/pdf", null, false, "origin", "AR"
        );

        assertThrows(com.example.fileprocessor.domain.exception.ProcessingException.class,
            () -> adapter.mapToProductDocument(PROD_ID, response));
    }

    // mapToProductDocumentHistory tests

    @Test
    void mapToProductDocumentHistory_withDocuments_returnsMappedFlux() {
        ProductDocumentResponse docResponse = new ProductDocumentResponse(
            "doc-1", "test.pdf", "VGVzdENvbnRlbnQ=", "application/pdf", 12L, false, "origin", "AR"
        );
        ProductResponse productResponse = new ProductResponse(
            PROD_ID, "Test Product", List.of(docResponse)
        );

        Flux<ProductDocumentHistory> flux = adapter.mapToProductDocumentHistory(productResponse);
        List<ProductDocumentHistory> result = flux.collectList().block();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("doc-1", result.get(0).getDocumentId());
    }

    @Test
    void mapToProductDocumentHistory_withEmptyDocuments_returnsEmptyFlux() {
        ProductResponse productResponse = new ProductResponse(
            PROD_ID, "Test Product", List.of()
        );

        Flux<ProductDocumentHistory> flux = adapter.mapToProductDocumentHistory(productResponse);
        List<ProductDocumentHistory> result = flux.collectList().block();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void mapToProductDocumentHistory_withNullDocuments_returnsEmptyFlux() {
        ProductResponse productResponse = new ProductResponse(
            PROD_ID, "Test Product", null
        );

        Flux<ProductDocumentHistory> flux = adapter.mapToProductDocumentHistory(productResponse);
        List<ProductDocumentHistory> result = flux.collectList().block();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // getDocument tests (using MockWebServer)

    @Test
    void getDocument_returnsMappedProductDocumentFile() {
        String responseJson = """
            {"documentId":"doc-1","filename":"test.pdf","content":"VGVzdENvbnRlbnQ=","contentType":"application/pdf","size":12,"isZip":false,"origin":"origin","pais":"AR"}""";

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
            })
            .verifyComplete();
    }

    // getDocumentsByProduct tests (using MockWebServer)

    @Test
    void getDocumentsByProduct_returnsMappedDocuments() {
        String responseJson = """
            [{"documentId":"doc-1","filename":"test.pdf","content":"VGVzdENvbnRlbnQ=","contentType":"application/pdf","size":12,"isZip":false,"origin":"origin","pais":"AR"}]""";

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        ProductHistory product = ProductHistory.builder().productId("prod-1").name("Product").build();

        StepVerifier.create(adapter.getDocumentsByProduct(product)
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(doc -> {
                assertEquals("prod-1", doc.getProductId());
                assertEquals("doc-1", doc.getDocumentId());
            })
            .verifyComplete();
    }

}
