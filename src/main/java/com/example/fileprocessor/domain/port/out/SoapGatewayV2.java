package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import reactor.core.publisher.Mono;

/**
 * Port for sending documents via SOAP V2 protocol (transmitirDocumento).
 */
public interface SoapGatewayV2 {
    Mono<FileUploadResponse> transmitirDocumento(FileUploadRequest request);
}
