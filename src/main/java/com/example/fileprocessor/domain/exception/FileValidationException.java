package com.example.fileprocessor.domain.exception;

public class FileValidationException extends DomainException {

    public FileValidationException(String message, String errorCode) {
        super(message, errorCode);
    }
}
