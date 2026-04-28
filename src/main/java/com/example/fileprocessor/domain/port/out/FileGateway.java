package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import reactor.core.publisher.Mono;

/**
 * Unified port for sending documents to external services (SOAP, S3, etc.)
 */
public interface FileGateway {
    /**
     * Sends a document to an external service.
     * @param request the document send request containing file data and metadata
     * @return Mono with the result of the send operation
     */
    Mono<FileUploadResult> send(DocumentSendRequest request);
}
