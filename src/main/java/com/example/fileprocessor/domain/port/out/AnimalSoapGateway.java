package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import reactor.core.publisher.Flux;

/**
 * Specialty SOAP gateway interface for upload operations of Animal documents.
 */
public interface AnimalSoapGateway {
    Flux<FileUploadResponse> send(FileUploadRequest request);
}
