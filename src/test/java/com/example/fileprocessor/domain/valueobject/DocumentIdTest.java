package com.example.fileprocessor.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIdTest {

    @Test
    void of_shouldCreateValidDocumentId() {
        DocumentId docId = DocumentId.of("doc-123");

        assertNotNull(docId);
        assertEquals("doc-123", docId.value());
        assertEquals("doc-123", docId.toString());
    }

    @Test
    void constructor_withValidValue_shouldSucceed() {
        DocumentId docId = new DocumentId("valid-doc-id");

        assertEquals("valid-doc-id", docId.value());
    }

    @Test
    void constructor_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(null));
    }

    @Test
    void constructor_withBlankString_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentId("   "));
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(""));
    }

    @Test
    void of_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> DocumentId.of(null));
    }

    @Test
    void of_withBlank_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> DocumentId.of(""));
        assertThrows(IllegalArgumentException.class, () -> DocumentId.of("\t"));
    }

    @Test
    void equals_sameValue_shouldBeEqual() {
        DocumentId id1 = DocumentId.of("doc-1");
        DocumentId id2 = DocumentId.of("doc-1");

        assertEquals(id1, id2);
    }

    @Test
    void equals_differentValue_shouldNotBeEqual() {
        DocumentId id1 = DocumentId.of("doc-1");
        DocumentId id2 = DocumentId.of("doc-2");

        assertNotEquals(id1, id2);
    }

    @Test
    void toString_shouldReturnValue() {
        DocumentId docId = DocumentId.of("my-doc");

        assertEquals("my-doc", docId.toString());
    }
}