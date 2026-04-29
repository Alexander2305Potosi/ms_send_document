package com.example.fileprocessor.infrastructure.helpers.soap.exception;

import com.example.fileprocessor.domain.exception.CommunicationException;

public class SoapCommunicationException extends CommunicationException {

    public SoapCommunicationException(String message, String errorCode, String traceId) {
        super(message + " [traceId=" + traceId + "]", errorCode);
    }

    public SoapCommunicationException(String message, String errorCode, String traceId, Throwable cause) {
        super(message + " [traceId=" + traceId + "]", errorCode, cause);
    }
}
