package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.HEADER_TRACE_ID;

/**
 * Handler for product-related REST endpoints.
 */
@Component
public class ProductHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductHandler.class);

    private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
    private final ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;

    public ProductHandler(
            SoapDocumentProcessingUseCase soapDocumentUseCase,
            ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider) {
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        String processorType = request.queryParam(ApiConstants.PARAM_PROCESSOR)
            .map(String::toLowerCase)
            .orElse(ApiConstants.PROCESSOR_SOAP);
        String traceId = resolveTraceId(request);

        return Mono.deferContextual(ctx -> {
            var results = getProcessor(processorType).executePendingDocuments()
                .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                    result.getCorrelationId(), result.getStatus()))
                .doOnError(error -> log.error("Processing failed for traceId {}: {}", traceId, error.getMessage()));

            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(results, com.example.fileprocessor.domain.entity.FileUploadResult.class);
        }).contextWrite(ctx -> ctx.put(HEADER_TRACE_ID, traceId));
    }

    private AbstractDocumentProcessingUseCase getProcessor(String processorType) {
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

    private static String resolveTraceId(ServerRequest request) {
        String header = request.headers().firstHeader(ApiConstants.HEADER_TRACE_ID);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
