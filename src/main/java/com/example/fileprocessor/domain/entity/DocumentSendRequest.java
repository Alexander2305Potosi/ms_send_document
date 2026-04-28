package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Request representation for sending files to external services (SOAP, S3, etc.).
 * Domain does NOT do Base64 encoding - that's infrastructure's responsibility.
 * Domain only transports raw data (byte[]).
 */
@Getter
@Builder
public class DocumentSendRequest {
    private final String documentId;
    private final byte[] fileContent;
    private final String filename;
    private final String contentType;
    private final long fileSize;
    private final String traceId;
    private final String parentFolder;
    private final String childFolder;
    private final String idempotencyKey;
}
