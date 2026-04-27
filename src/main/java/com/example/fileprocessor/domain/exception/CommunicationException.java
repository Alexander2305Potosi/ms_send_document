package com.example.fileprocessor.domain.exception;

/**
 * Domain exception for communication errors (SOAP, HTTP, etc.).
 * This exception lives in the domain layer to maintain Hexagonal Architecture principles,
 * ensuring the domain is not coupled to infrastructure exceptions.
 */
public class CommunicationException extends DomainException {

    private final String traceId;
    private final int retryCount;

    public CommunicationException(String message, String errorCode, String traceId) {
        this(message, errorCode, traceId, 0);
    }

    public CommunicationException(String message, String errorCode, String traceId, int retryCount) {
        super(message, errorCode);
        this.traceId = traceId;
        this.retryCount = retryCount;
    }

    public CommunicationException(String message, String errorCode, String traceId, Throwable cause) {
        super(message, errorCode);
        this.traceId = traceId;
        this.retryCount = 0;
        initCause(cause);
    }

    public String getTraceId() {
        return traceId;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
