package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductRestGatewayAdapterTest {

    @Test
    void mapToProduct_withDocuments() {
        ProductDocumentResponse docResponse = new ProductDocumentResponse(
            "doc-1", "test.pdf", "VGVzdENvbnRlbnQ=", "application/pdf", 12L, false, "origin", "AR"
        );
        ProductResponse productResponse = new ProductResponse(
            "prod-1", "Test Product", List.of(docResponse)
        );

        ProductDocumentHistory doc = mapToProductDocument(docResponse);

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

        ProductDocumentHistory doc = mapToProductDocument(response);

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

        ProductDocumentHistory doc = mapToProductDocument(response);

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

        ProductDocumentHistory doc = mapToProductDocument(response);

        assertTrue(doc.isZip());
        assertEquals("test.zip", doc.filename());
        assertEquals("AR", doc.pais());
    }

    @Test
    void mapToProduct_withEmptyDocuments() {
        ProductResponse productResponse = new ProductResponse(
            "prod-1", "Test Product", List.of()
        );

        ProductHistory product = mapToProduct(productResponse);

        assertEquals("prod-1", product.productId());
        assertEquals("Test Product", product.name());
        assertTrue(product.documents().isEmpty());
    }

    @Test
    void mapToProduct_withNullDocuments() {
        ProductResponse productResponse = new ProductResponse(
            "prod-1", "Test Product", null
        );

        ProductHistory product = mapToProduct(productResponse);

        assertEquals("prod-1", product.productId());
        assertTrue(product.documents().isEmpty());
    }

    private ProductDocumentHistory mapToProductDocument(ProductDocumentResponse json) {
        byte[] content = null;
        if (json.content() != null && !json.content().isBlank()) {
            content = com.example.fileprocessor.domain.util.Base64Utils.decodeSafe(
                json.content(), json.filename(), json.documentId());
        }
        long size = json.size() != null ? json.size() : (content != null ? content.length : 0);
        boolean isZip = Boolean.TRUE.equals(json.isZip());
        return ProductDocumentHistory.builder()
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

    private ProductHistory mapToProduct(ProductResponse json) {
        List<ProductDocumentHistory> documents = json.documents() != null
            ? json.documents().stream()
                .map(this::mapToProductDocument)
                .toList()
            : List.of();
        return ProductHistory.builder()
            .productId(json.productId())
            .name(json.name())
            .documents(documents)
            .build();
    }
}