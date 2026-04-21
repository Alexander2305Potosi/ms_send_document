package com.example.fileprocessor.infrastructure.soap.exception;

public class SoapCommunicationException extends RuntimeException {

    private final String errorCode;
    private final String traceId;

    public SoapCommunicationException(String message, String errorCode, String traceId) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTraceId() {
        return traceId;
    }
}
