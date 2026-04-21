package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class ProcessFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessFileUseCase.class);
    private final ExternalSoapGateway soapGateway;
    private final FileValidator fileValidator;

    public ProcessFileUseCase(ExternalSoapGateway soapGateway,
                               FileValidator fileValidator) {
        this.soapGateway = soapGateway;
        this.fileValidator = fileValidator;
    }

    public Mono<FileUploadResult> execute(FileData fileData) {
        return Mono.just(fileData)
            .doOnNext(data -> log.info("Processing file: {}, traceId: {}",
                data.filename(), data.traceId()))
            .flatMap(fileValidator::validate)
            .map(SoapRequest::fromFileData)
            .flatMap(soapGateway::sendFile)
            .map(this::toResult)
            .doOnNext(response -> log.info("File {} processed successfully, correlationId: {}",
                fileData.filename(), response.correlationId()))
            .doOnError(error -> log.error("Error processing file {}: {}",
                fileData.filename(), error.getMessage()));
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
