package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import reactor.core.publisher.Flux;

/**
 * Port for sending documents via SOAP protocol.
 */
public interface SoapGateway {
    Flux<FileUploadResponse> send(FileUploadRequest request);
}
