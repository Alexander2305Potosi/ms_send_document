package com.example.fileprocessor.domain.exception;

import org.junit.jupiter.api.Test;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingExceptionTest {

    @Test
    void constructor_withTraceId() {
        ProcessingException ex = new ProcessingException("error", "ERR_CODE", "trace-123");

        assertEquals("error", ex.getMessage());
        assertEquals("ERR_CODE", ex.getErrorCode());
        assertEquals("trace-123", ex.getTraceId());
        assertNull(ex.getDocumentId());
    }

    @Test
    void constructor_withTraceIdAndDocumentId() {
        ProcessingException ex = new ProcessingException("error", "ERR_CODE", "trace-123", "doc-1");

        assertEquals("error", ex.getMessage());
        assertEquals("ERR_CODE", ex.getErrorCode());
        assertEquals("trace-123", ex.getTraceId());
        assertEquals("doc-1", ex.getDocumentId());
    }

    @Test
    void constructor_withCause() {
        Throwable cause = new RuntimeException("original");
        ProcessingException ex = new ProcessingException("error", "ERR_CODE", "trace-123", cause);

        assertEquals("error", ex.getMessage());
        assertEquals("trace-123", ex.getTraceId());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void constructor_withNullTraceId_defaultsToUnknown() {
        ProcessingException ex = new ProcessingException("error", "ERR_CODE", null);

        assertEquals("unknown", ex.getTraceId());
    }

    @Test
    void withTraceId_factoryMethod() {
        ProcessingException ex = ProcessingException.withTraceId("error", "ERR_CODE", "trace-456");

        assertTrue(ex.getMessage().contains("trace-456"));
        assertEquals("ERR_CODE", ex.getErrorCode());
    }

    @Test
    void withTraceId_factoryMethodWithCause() {
        Throwable cause = new RuntimeException("original");
        ProcessingException ex = ProcessingException.withTraceId("error", "ERR_CODE", "trace-456", cause);

        assertTrue(ex.getMessage().contains("trace-456"));
        assertEquals(cause, ex.getCause());
    }

    @Test
    void fromContext_extractsTraceIdFromContext() {
        ContextView ctx = Context.of("message-id", "ctx-trace-789");
        ProcessingException ex = ProcessingException.fromContext(ctx, "error", "ERR_CODE");

        assertTrue(ex.getMessage().contains("ctx-trace-789"));
        assertEquals("ERR_CODE", ex.getErrorCode());
    }

    @Test
    void fromContext_withDocumentId() {
        ContextView ctx = Context.of("message-id", "ctx-trace-789");
        ProcessingException ex = ProcessingException.fromContext(ctx, "error", "ERR_CODE", "doc-ctx");

        assertEquals("doc-ctx", ex.getDocumentId());
        assertEquals("ERR_CODE", ex.getErrorCode());
    }

    @Test
    void fromContext_whenKeyMissing_defaultsToUnknown() {
        ContextView ctx = Context.empty();
        ProcessingException ex = ProcessingException.fromContext(ctx, "error", "ERR_CODE");

        assertEquals("unknown", ex.getTraceId());
    }
}
