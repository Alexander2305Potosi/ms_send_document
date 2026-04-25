package com.example.fileprocessor.domain.usecase;

/**
 * Constants for S3 document processing use case.
 */
public final class S3UseCaseConstants {

    private S3UseCaseConstants() {}

    public static final String MSG_UPLOAD_SUCCESS = "Uploaded to S3: ";
    public static final String MSG_UPLOAD_FAILURE = "S3 upload failed: ";
    public static final String IMPL_NAME = "S3";
}