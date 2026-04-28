package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import reactor.core.publisher.Mono;

/**
 * Strategy interface for document sending implementations.
 * Decouples the sending mechanism (SOAP, S3, FTP, etc.) from the processing orchestrator.
 */
@FunctionalInterface
public interface DocumentSender {
    Mono<FileUploadResult> send(DocumentSendRequest request);
}