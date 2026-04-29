package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Handler for product-related REST endpoints.
 */
@Component
public class ProductHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductHandler.class);

    private final LoadProductsUseCase loadProductsUseCase;
    private final SoapDocumentProcessingUseCase soapDocumentUseCase;
    private final S3DocumentProcessingUseCase s3DocumentUseCase;

    public ProductHandler(LoadProductsUseCase loadProductsUseCase,
                         SoapDocumentProcessingUseCase soapDocumentUseCase,
                         S3DocumentProcessingUseCase s3DocumentUseCase) {
        this.loadProductsUseCase = loadProductsUseCase;
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCase = s3DocumentUseCase;
    }

    public Mono<ServerResponse> loadProducts(ServerRequest request) {
        String traceId = resolveTraceId(request);
        log.info("Starting products load from REST API, traceId: {}", traceId);

        return ServerResponse.accepted()
            .bodyValue(loadProductsUseCase.execute()
                .doOnNext(result -> log.info("Product loaded: {} -> {} ({} documents)",
                    result.getProductId(), result.getStatus(), result.getDocumentCount()))
                .doOnError(error -> log.error("Load failed for traceId {}: {}", traceId, error.getMessage()))
                .thenMany(Mono.empty())
            );
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        String processorType = request.queryParam(ApiConstants.PARAM_PROCESSOR).orElse(ApiConstants.PROCESSOR_SOAP);
        String traceId = resolveTraceId(request);

        if (ApiConstants.PROCESSOR_S3.equalsIgnoreCase(processorType)) {
            log.info("Starting S3 document processing, traceId: {}", traceId);
            return ServerResponse.accepted()
                .bodyValue(s3DocumentUseCase.executePendingDocuments()
                    .doOnNext(result -> log.info("S3 Document processed: correlationId={}, status={}",
                        result.getCorrelationId(), result.getStatus()))
                    .doOnError(error -> log.error("S3 Processing failed for traceId {}: {}", traceId, error.getMessage()))
                    .collectList()
                );
        }

        if (!ApiConstants.PROCESSOR_SOAP.equalsIgnoreCase(processorType)) {
            log.warn("Unknown processor type '{}', defaulting to SOAP, traceId: {}", processorType, traceId);
        }

        log.info("Starting SOAP document processing, traceId: {}", traceId);
        return ServerResponse.accepted()
            .bodyValue(soapDocumentUseCase.executePendingDocuments()
                .doOnNext(result -> log.info("SOAP Document processed: correlationId={}, status={}",
                    result.getCorrelationId(), result.getStatus()))
                .doOnError(error -> log.error("SOAP Processing failed for traceId {}: {}", traceId, error.getMessage()))
                .collectList()
            );
    }

    private static String resolveTraceId(ServerRequest request) {
        String header = request.headers().firstHeader(ApiConstants.HEADER_TRACE_ID);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}