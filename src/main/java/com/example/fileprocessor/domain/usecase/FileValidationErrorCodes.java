package com.example.fileprocessor.domain.usecase;

/**
 * Error codes and messages for file validation.
 */
public final class FileValidationErrorCodes {

    private FileValidationErrorCodes() {}

    public static final String FILE_SIZE_EXCEEDED = "FILE_SIZE_EXCEEDED";
    public static final String INVALID_FILE_TYPE = "INVALID_FILE_TYPE";
    public static final String FILENAME_TOO_LONG = "FILENAME_TOO_LONG";
    public static final String INVALID_FILENAME = "INVALID_FILENAME";

    public static final String MSG_FILE_SIZE_EXCEEDED = "File size exceeds maximum allowed: ";
    public static final String MSG_FILE_TYPE_NOT_ALLOWED = "File type not allowed. Allowed types: ";
    public static final String MSG_FILENAME_TOO_LONG = "Filename exceeds maximum length: ";
    public static final String MSG_FILENAME_INVALID = "Filename contains invalid characters";
}