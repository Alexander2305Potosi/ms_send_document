package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractDocumentProcessingUseCase;
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
    private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
    private final AbstractDocumentProcessingUseCase s3DocumentUseCase;

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
        String processorType = request.queryParam(ApiConstants.PARAM_PROCESSOR)
            .map(String::toLowerCase)
            .orElse(ApiConstants.PROCESSOR_SOAP);
        String traceId = resolveTraceId(request);

        return ServerResponse.accepted()
            .bodyValue(getProcessor(processorType, traceId).executePendingDocuments()
                .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                    result.getCorrelationId(), result.getStatus()))
                .doOnError(error -> log.error("Processing failed for traceId {}: {}", traceId, error.getMessage()))
                .collectList());
    }

    private AbstractDocumentProcessingUseCase getProcessor(String processorType, String traceId) {
        if (ApiConstants.PROCESSOR_S3.equals(processorType)) {
            if (s3DocumentUseCase == null) {
                throw new IllegalStateException("S3 processor not available - enable 's3' profile");
            }
            log.info("Using S3 processor, traceId: {}", traceId);
            return s3DocumentUseCase;
        }
        if (!ApiConstants.PROCESSOR_SOAP.equals(processorType)) {
            log.warn("Unknown processor type '{}', defaulting to SOAP, traceId: {}", processorType, traceId);
        }
        log.info("Using SOAP processor, traceId: {}", traceId);
        return soapDocumentUseCase;
    }

    private static String resolveTraceId(ServerRequest request) {
        String header = request.headers().firstHeader(ApiConstants.HEADER_TRACE_ID);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
