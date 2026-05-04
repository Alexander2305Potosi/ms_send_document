package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductHistoryTest {

    @Test
    void product_record_createsValidProduct() {
        ProductDocumentHistory doc = new ProductDocumentHistory("doc-1", "test.pdf", new byte[0], "application/pdf", 100L, false, "origin", "AR");
        ProductHistory product = new ProductHistory(null, "prod-1", "Test Product", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        assertEquals("prod-1", product.productId());
        assertEquals("Test Product", product.name());
        assertEquals(1, product.documents().size());
        assertEquals("doc-1", product.documents().get(0).documentId());
    }

    @Test
    void product_withEmptyDocuments() {
        ProductHistory product = new ProductHistory(null, "prod-1", "Empty Product", LocalDateTime.now(), "ACTIVE", null, List.of());

        assertEquals("prod-1", product.productId());
        assertEquals("Empty Product", product.name());
        assertTrue(product.documents().isEmpty());
    }
}
