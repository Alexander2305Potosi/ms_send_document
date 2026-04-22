package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

public class ProcessFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessFileUseCase.class);
    private final ExternalSoapGateway soapGateway;
    private final FileValidator fileValidator;
    private final SoapCommunicationLogRepository logRepository;

    public ProcessFileUseCase(ExternalSoapGateway soapGateway,
                               FileValidator fileValidator,
                               SoapCommunicationLogRepository logRepository) {
        this.soapGateway = soapGateway;
        this.fileValidator = fileValidator;
        this.logRepository = logRepository;
    }

    public Mono<FileUploadResult> execute(FileData fileData) {
        return Mono.just(fileData)
            .doOnNext(data -> log.info("Processing file: {}, traceId: {}",
                data.filename(), data.traceId()))
            .flatMap(fileValidator::validate)
            .map(SoapRequest::fromFileData)
            .flatMap(soapGateway::sendFile)
            .flatMap(response -> logSuccess(fileData, response).thenReturn(response))
            .map(this::toResult)
            .doOnNext(response -> log.info("File {} processed successfully, correlationId: {}",
                fileData.filename(), response.correlationId()))
            .doOnError(error -> log.error("Error processing file {}: {}",
                fileData.filename(), error.getMessage()))
            .onErrorResume(throwable -> isSoapCommunicationError(throwable)
                ? logFailure(fileData, throwable).then(Mono.error(throwable))
                : Mono.error(throwable));
    }

    private boolean isSoapCommunicationError(Throwable throwable) {
        return throwable instanceof SoapCommunicationException
            || throwable.getCause() instanceof SoapCommunicationException;
    }

    private Mono<Void> logSuccess(FileData fileData, SoapResponse response) {
        SoapCommunicationLog dbLog = new SoapCommunicationLog(
            response.traceId(),
            "SUCCESS",
            0,
            null,
            fileData.filename(),
            Instant.now()
        );
        return logRepository.save(dbLog).then();
    }

    private Mono<Void> logFailure(FileData fileData, Throwable error) {
        String errorCode = extractErrorCode(error);
        int retries = extractRetryCount(error);
        SoapCommunicationLog dbLog = new SoapCommunicationLog(
            fileData.traceId(),
            "FAILURE",
            retries,
            errorCode,
            fileData.filename(),
            Instant.now()
        );
        return logRepository.save(dbLog).then();
    }

    private String extractErrorCode(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        return "UNKNOWN_ERROR";
    }

    private int extractRetryCount(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce.getRetryCount();
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce.getRetryCount();
        }
        return 0;
    }

    private FileUploadResult toResult(SoapResponse response) {
        return new FileUploadResult(
            response.status(),
            response.message(),
            response.correlationId(),
            response.traceId(),
            response.processedAt(),
            response.externalReference(),
            response.isSuccess()
        );
    }
}
