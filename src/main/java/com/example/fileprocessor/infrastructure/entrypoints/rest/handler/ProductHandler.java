package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.HEADER_TRACE_ID;
import static com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.HEADER_USE_CASE;

@Component
public class ProductHandler {

    private static final Logger log = Logger.getLogger(ProductHandler.class.getName());

    private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
    private final ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
    private final SyncDocumentsUseCase syncDocumentsUseCase;

    public ProductHandler(
            SoapDocumentProcessingUseCase soapDocumentUseCase,
            ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider,
            SyncDocumentsUseCase syncDocumentsUseCase) {
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
        this.syncDocumentsUseCase = syncDocumentsUseCase;
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        String processorType = request.queryParam(ApiConstants.PARAM_PROCESSOR)
            .map(String::toLowerCase)
            .orElse(ApiConstants.PROCESSOR_SOAP);
        String traceId = resolveTraceId(request);

        return Mono.deferContextual(ctx -> {
            var results = getProcessor(processorType).executePendingDocuments()
                .doOnNext(result -> log.log(Level.INFO, "Document processed: correlationId={0}, status={1}",
                    new Object[]{result.getCorrelationId(), result.getStatus()}))
                .doOnError(error -> log.log(Level.SEVERE, "Processing failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}));

            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(results, com.example.fileprocessor.domain.entity.FileUploadResponse.class);
        }).contextWrite(ctx -> ctx.put(HEADER_TRACE_ID, traceId));
    }

    public Mono<ServerResponse> syncProducts(ServerRequest request) {
        String traceId = resolveTraceId(request);
        String useCase = request.headers().firstHeader(HEADER_USE_CASE);

        return Mono.deferContextual(ctx -> {
            log.log(Level.INFO, "Starting document sync, traceId: {0}, useCase: {1}", new Object[]{traceId, useCase});
            syncDocumentsUseCase.execute(useCase)
                .doOnError(error -> log.log(Level.SEVERE, "Document sync failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnSuccess(v -> log.log(Level.INFO, "Document sync completed for traceId: {0}", new Object[]{traceId}))
                .contextWrite(ctx)
                .subscribe();
            return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("status", "OK", "message", "Document sync initiated"));
        }).contextWrite(ctx -> ctx.put(HEADER_TRACE_ID, traceId));
    }

    AbstractDocumentProcessingUseCase getProcessor(String processorType) {
        return switch (processorType) {
            case ApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
            case ApiConstants.PROCESSOR_S3 -> {
                S3DocumentProcessingUseCase s3UseCase = s3DocumentUseCaseProvider.getIfAvailable();
                if (s3UseCase == null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "S3 processor not available - enable 's3' profile");
                }
                yield s3UseCase;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown processor type: '" + processorType + "'. Valid values: soap, s3");
        };
    }

    static String resolveTraceId(ServerRequest request) {
        String header = request.headers().firstHeader(ApiConstants.HEADER_TRACE_ID);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}