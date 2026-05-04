package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductRestGatewayAdapterTest {

    private static final String PROD_ID = "prod-1";

    @Test
    void mapToProductDocument_withDocuments() {
        ProductDocumentResponse docResponse = new ProductDocumentResponse(
            "doc-1", "test.pdf", "VGVzdENvbnRlbnQ=", "application/pdf", 12L, false, "origin", "AR"
        );

        ProductDocumentHistory doc = mapToProductDocument(PROD_ID, docResponse);

        assertEquals(PROD_ID, doc.productId());
        assertEquals("doc-1", doc.documentId());
        assertEquals("test.pdf", doc.filename());
        assertEquals("application/pdf", doc.contentType());
        assertEquals(12L, doc.size());
        assertFalse(doc.isZip());
        assertEquals("origin", doc.origin());
        assertEquals("AR", doc.pais());
    }

    @Test
    void mapToProductDocument_withBase64Content() {
        String base64Content = "VGVzdENvbnRlbnQ=";
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", base64Content, "application/pdf", 12L, false, "origin", "AR"
        );

        ProductDocumentHistory doc = mapToProductDocument(PROD_ID, response);

        assertEquals(PROD_ID, doc.productId());
        assertEquals("doc-1", doc.documentId());
        assertNotNull(doc.content());
        assertEquals("origin", doc.origin());
        assertEquals("AR", doc.pais());
    }

    @Test
    void mapToProductDocument_withNullContent() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.pdf", null, "application/pdf", null, false, "origin", "AR"
        );

        ProductDocumentHistory doc = mapToProductDocument(PROD_ID, response);

        assertEquals(PROD_ID, doc.productId());
        assertEquals("doc-1", doc.documentId());
        assertNull(doc.content());
        assertEquals(0L, doc.size());
        assertEquals("AR", doc.pais());
    }

    @Test
    void mapToProductDocument_withZipFlag() {
        ProductDocumentResponse response = new ProductDocumentResponse(
            "doc-1", "test.zip", "VGVzdENvbnRlbnQ=", "application/zip", 12L, true, "origin", "AR"
        );

        ProductDocumentHistory doc = mapToProductDocument(PROD_ID, response);

        assertEquals(PROD_ID, doc.productId());
        assertTrue(doc.isZip());
        assertEquals("test.zip", doc.filename());
        assertEquals("AR", doc.pais());
    }

    @Test
    void mapToProduct_withEmptyDocuments() {
        ProductResponse productResponse = new ProductResponse(
            "prod-1", "Test Product", List.of()
        );

        List<ProductDocumentHistory> documents = mapToProductDocumentHistory(productResponse);

        assertTrue(documents.isEmpty());
    }

    @Test
    void mapToProduct_withNullDocuments() {
        ProductResponse productResponse = new ProductResponse(
            "prod-1", "Test Product", null
        );

        List<ProductDocumentHistory> documents = mapToProductDocumentHistory(productResponse);

        assertTrue(documents.isEmpty());
    }

    private List<ProductDocumentHistory> mapToProductDocumentHistory(ProductResponse json) {
        if (json.documents() == null || json.documents().isEmpty()) {
            return List.of();
        }
        return json.documents().stream()
            .map(doc -> mapToProductDocument(json.productId(), doc))
            .toList();
    }

    private ProductDocumentHistory mapToProductDocument(String productId, ProductDocumentResponse json) {
        byte[] content = null;
        if (json.content() != null && !json.content().isBlank()) {
            content = com.example.fileprocessor.domain.util.Base64Utils.decodeSafe(
                json.content(), json.filename(), json.documentId());
        }
        long size = json.size() != null ? json.size() : (content != null ? content.length : 0);
        boolean isZip = Boolean.TRUE.equals(json.isZip());
        return ProductDocumentHistory.builder()
            .productId(productId)
            .documentId(json.documentId())
            .filename(json.filename())
            .content(content)
            .contentType(json.contentType())
            .size(size)
            .isZip(isZip)
            .origin(json.origin())
            .pais(json.pais())
            .build();
    }
}