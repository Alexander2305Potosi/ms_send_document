package com.example.fileprocessor.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdTest {

    @Test
    void random_shouldCreateValidTraceId() {
        TraceId traceId = TraceId.random();

        assertNotNull(traceId);
        assertNotNull(traceId.value());
        // UUID format validation
        assertDoesNotThrow(() -> UUID.fromString(traceId.value()));
    }

    @Test
    void of_shouldCreateFromString() {
        TraceId traceId = TraceId.of("custom-trace-id");

        assertEquals("custom-trace-id", traceId.value());
    }

    @Test
    void constructor_withValidValue_shouldSucceed() {
        TraceId traceId = new TraceId("valid-trace");

        assertEquals("valid-trace", traceId.value());
    }

    @Test
    void constructor_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TraceId(null));
    }

    @Test
    void constructor_withBlank_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TraceId("   "));
        assertThrows(IllegalArgumentException.class, () -> new TraceId(""));
    }

    @Test
    void of_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> TraceId.of(null));
    }

    @Test
    void of_withBlank_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> TraceId.of(""));
    }

    @Test
    void random_eachCall_shouldGenerateUnique() {
        TraceId id1 = TraceId.random();
        TraceId id2 = TraceId.random();

        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void equals_sameValue_shouldBeEqual() {
        TraceId id1 = TraceId.of("trace-1");
        TraceId id2 = TraceId.of("trace-1");

        assertEquals(id1, id2);
    }

    @Test
    void equals_differentValue_shouldNotBeEqual() {
        TraceId id1 = TraceId.of("trace-1");
        TraceId id2 = TraceId.of("trace-2");

        assertNotEquals(id1, id2);
    }

    @Test
    void toString_shouldReturnValue() {
        TraceId traceId = TraceId.of("my-trace");

        assertEquals("my-trace", traceId.toString());
    }
}