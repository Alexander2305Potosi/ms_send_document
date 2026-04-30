package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import reactor.core.publisher.Mono;

/**
 * Port for sending documents via SOAP protocol.
 */
public interface SoapGateway {
    Mono<FileUploadResult> send(FileUploadRequest request);
}
