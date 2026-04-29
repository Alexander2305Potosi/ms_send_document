package com.example.fileprocessor.domain.exception;

/**
 * Domain exception for communication errors (SOAP, HTTP, etc.).
 * This exception lives in the domain layer to maintain Hexagonal Architecture principles,
 * ensuring the domain is not coupled to infrastructure exceptions.
 */
public class CommunicationException extends DomainException {

    public CommunicationException(String message, String errorCode) {
        super(message, errorCode);
    }

    public CommunicationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }

    public static CommunicationException withTraceId(String message, String errorCode, String traceId) {
        return new CommunicationException(message + " [traceId=" + traceId + "]", errorCode);
    }
}
