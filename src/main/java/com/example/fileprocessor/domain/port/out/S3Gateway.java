package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import reactor.core.publisher.Mono;

/**
 * Port for sending documents to S3.
 */
public interface S3Gateway {
    Mono<FileUploadResponse> send(FileUploadRequest request);
}
