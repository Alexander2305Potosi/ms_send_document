package com.example.fileprocessor.infrastructure.soap.exception;

public class SoapCommunicationException extends RuntimeException {

    private final String errorCode;
    private final String traceId;
    private final int retryCount;

    public SoapCommunicationException(String message, String errorCode, String traceId) {
        this(message, errorCode, traceId, 0);
    }

    public SoapCommunicationException(String message, String errorCode, String traceId, int retryCount) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
        this.retryCount = retryCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
