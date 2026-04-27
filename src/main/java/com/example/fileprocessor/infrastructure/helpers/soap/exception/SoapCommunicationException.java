package com.example.fileprocessor.infrastructure.helpers.soap.exception;

import com.example.fileprocessor.domain.exception.CommunicationException;

public class SoapCommunicationException extends CommunicationException {

    public SoapCommunicationException(String message, String errorCode, String traceId) {
        super(message, errorCode, traceId, 0);
    }

    public SoapCommunicationException(String message, String errorCode, String traceId, int retryCount) {
        super(message, errorCode, traceId, retryCount);
    }

    public SoapCommunicationException(String message, String errorCode, String traceId, Throwable cause) {
        super(message, errorCode, traceId, cause);
    }
}
