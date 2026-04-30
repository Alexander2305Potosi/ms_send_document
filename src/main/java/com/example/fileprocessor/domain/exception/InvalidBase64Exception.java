package com.example.fileprocessor.domain.exception;

public class InvalidBase64Exception extends DomainException {

    public InvalidBase64Exception(String message, String errorCode) {
        super(message, errorCode);
    }

    public InvalidBase64Exception(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}