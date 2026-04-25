package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.SoapRequest;
import reactor.core.publisher.Mono;

/**
 * Port for sending documents to cloud storage services (S3, GCS, Azure Blob, etc.)
 */
public interface S3Gateway {

    /**
     * Upload a document to cloud storage.
     * @param request the document request containing file data and metadata
     * @return Mono with upload confirmation
     */
    Mono<S3UploadResult> upload(SoapRequest request);

    /**
     * Result of an S3 upload operation.
     */
    record S3UploadResult(
        String bucket,
        String key,
        String eTag,
        long size,
        String contentType
    ) {}
}