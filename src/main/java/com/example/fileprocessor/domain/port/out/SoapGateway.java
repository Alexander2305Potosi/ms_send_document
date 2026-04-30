package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import reactor.core.publisher.Mono;

/**
 * Port for sending documents via SOAP protocol.
 */
public interface SoapGateway {
    Mono<FileUploadResult> sendSoap(String documentId, byte[] content, String filename,
                                     String contentType, long fileSize,
                                     String parentFolder, String childFolder);
}
