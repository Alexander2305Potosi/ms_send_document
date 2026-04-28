package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.CommunicationLog;
import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.FileGateway;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Unified implementation of DocumentSender.
 * Uses FileGateway for actual document sending (SOAP, S3, etc.)
 */
public class DocumentSenderImpl implements DocumentSender {

    private final FileGateway fileGateway;
    private final CommunicationLogRepository logRepository;

    public DocumentSenderImpl(FileGateway fileGateway, CommunicationLogRepository logRepository) {
        this.fileGateway = fileGateway;
        this.logRepository = logRepository;
    }

    @Override
    public Mono<FileUploadResult> send(DocumentSendRequest request) {
        return fileGateway.send(request)
            .flatMap(result -> saveSuccessLog(request.getDocumentId(), request.getFilename(), request.getTraceId(), result)
                .thenReturn(result));
    }

    private Mono<Void> saveSuccessLog(String documentId, String filename, String traceId, FileUploadResult result) {
        CommunicationLog dbLog = CommunicationLog.builder()
            .traceId(traceId)
            .documentId(documentId)
            .status(result.getStatus())
            .retryCount(ProcessingMessages.DEFAULT_RETRY_COUNT)
            .filename(filename)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }
}
