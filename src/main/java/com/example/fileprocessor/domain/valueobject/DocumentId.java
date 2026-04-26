package com.example.fileprocessor.domain.valueobject;

/**
 * Value Object for document identification.
 * Ensures non-blank document IDs provide type safety.
 */
public record DocumentId(String value) {

    public DocumentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DocumentId cannot be blank");
        }
    }

    public static DocumentId of(String value) {
        return new DocumentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
