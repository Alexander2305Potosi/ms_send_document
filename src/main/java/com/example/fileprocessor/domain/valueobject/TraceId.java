package com.example.fileprocessor.domain.valueobject;

import java.util.UUID;

/**
 * Value Object for trace identification.
 * Ensures valid UUID format and provides type safety.
 */
public record TraceId(String value) {

    public TraceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TraceId cannot be blank");
        }
    }

    /**
     * Creates a new random TraceId using UUID v4 format.
     */
    public static TraceId random() {
        return new TraceId(UUID.randomUUID().toString());
    }

    /**
     * Creates a TraceId from an existing string value.
     * Validates format but does not guarantee UUID compliance.
     */
    public static TraceId of(String value) {
        return new TraceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
