package com.example.fileprocessor.application.usecase;

import com.example.fileprocessor.application.dto.FileUploadResponseDto;
import com.example.fileprocessor.application.mapper.FileMapper;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ProcessFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessFileUseCase.class);
    private final ExternalSoapGateway soapGateway;
    private final FileValidator fileValidator;
    private final FileMapper fileMapper;

    public ProcessFileUseCase(ExternalSoapGateway soapGateway,
                               FileValidator fileValidator,
                               FileMapper fileMapper) {
        this.soapGateway = soapGateway;
        this.fileValidator = fileValidator;
        this.fileMapper = fileMapper;
    }

    public Mono<FileUploadResponseDto> execute(FileData fileData) {
        return Mono.just(fileData)
            .doOnNext(data -> log.info("Processing file: {}, traceId: {}",
                data.filename(), data.traceId()))
            .flatMap(fileValidator::validate)
            .map(SoapRequest::fromFileData)
            .flatMap(soapGateway::sendFile)
            .map(fileMapper::toResponseDto)
            .doOnNext(response -> log.info("File {} processed successfully, correlationId: {}",
                fileData.filename(), response.correlationId()))
            .doOnError(error -> log.error("Error processing file {}: {}",
                fileData.filename(), error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
