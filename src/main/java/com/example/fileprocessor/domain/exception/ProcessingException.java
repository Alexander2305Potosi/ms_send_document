package com.example.fileprocessor.domain.exception;

import reactor.util.context.ContextView;

/**
 * Unified exception for all gateway communication errors (SOAP, S3, REST).
 * Replaces CommunicationException and SoapCommunicationException.
 */
public class ProcessingException extends DomainException {

    private static final String DEFAULT_TRACE_ID = "unknown";

    public static final String HEADER_TRACE_ID = "message-id";

    private final String traceId;
    private final String documentId;

    public ProcessingException(String message, String errorCode, String traceId) {
        super(message, errorCode);
        this.traceId = traceId != null ? traceId : DEFAULT_TRACE_ID;
        this.documentId = null;
    }

    public ProcessingException(String message, String errorCode, String traceId, String documentId) {
        super(message, errorCode);
        this.traceId = traceId != null ? traceId : DEFAULT_TRACE_ID;
        this.documentId = documentId;
    }

    public ProcessingException(String message, String errorCode, String traceId, Throwable cause) {
        super(message, errorCode, cause);
        this.traceId = traceId != null ? traceId : DEFAULT_TRACE_ID;
        this.documentId = null;
    }

    public ProcessingException(String errorCode, String message) {
        super(message, errorCode);
        this.traceId = DEFAULT_TRACE_ID;
        this.documentId = null;
    }

    public ProcessingException(String errorCode, String message, Throwable cause) {
        super(message, errorCode, cause);
        this.traceId = DEFAULT_TRACE_ID;
        this.documentId = null;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public static ProcessingException withTraceId(String message, String errorCode, String traceId) {
        return new ProcessingException(message + " [traceId=" + traceId + "]", errorCode, traceId);
    }

    public static ProcessingException withTraceId(String message, String errorCode, String traceId, Throwable cause) {
        return new ProcessingException(message + " [traceId=" + traceId + "]", errorCode, traceId, cause);
    }

    public static ProcessingException fromContext(ContextView ctx, String message, String errorCode) {
        String traceId = ctx.getOrDefault(HEADER_TRACE_ID, DEFAULT_TRACE_ID);
        return new ProcessingException(message + " [traceId=" + traceId + "]", errorCode, traceId);
    }

    public static ProcessingException fromContext(ContextView ctx, String message, String errorCode, String documentId) {
        String traceId = ctx.getOrDefault(HEADER_TRACE_ID, DEFAULT_TRACE_ID);
        return new ProcessingException(message, errorCode, traceId, documentId);
    }
}
