package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractProcessDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.RestApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProductHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductHandler.class);

    private final LoadProductsUseCase loadProductsUseCase;
    private final SoapDocumentUseCase soapDocumentUseCase;
    private final S3DocumentUseCase s3DocumentUseCase;

    public ProductHandler(LoadProductsUseCase loadProductsUseCase,
                         SoapDocumentUseCase soapDocumentUseCase,
                         @Autowired(required = false) S3DocumentUseCase s3DocumentUseCase) {
        this.loadProductsUseCase = loadProductsUseCase;
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCase = s3DocumentUseCase;
    }

    public Mono<ServerResponse> loadProducts(ServerRequest request) {
        String traceId = UUID.randomUUID().toString();
        MDC.put(RestApiConstants.MDC_TRACE_ID, traceId);

        log.info("Starting products load from REST API, traceId: {}", traceId);

        loadProductsUseCase.execute()
            .doOnNext(result -> log.info("Product loaded: {} -> {} ({} documents)",
                result.getProductId(), result.getStatus(), result.getDocumentCount()))
            .doOnError(error -> log.error("Load failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove(RestApiConstants.MDC_TRACE_ID))
            .subscribe();

        return ServerResponse.accepted()
            .bodyValue(new AsyncProcessResponse(
                RestApiConstants.STATUS_LOADING,
                RestApiConstants.MSG_LOADING,
                null,
                traceId,
                null,
                Instant.now(),
                null,
                true
            ));
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        String processorType = request.queryParam(RestApiConstants.PARAM_PROCESSOR).orElse(RestApiConstants.PROCESSOR_SOAP);

        String traceId = UUID.randomUUID().toString();
        MDC.put(RestApiConstants.MDC_TRACE_ID, traceId);

        AbstractProcessDocumentsUseCase useCase = resolveUseCase(processorType);
        log.info("Starting pending product documents processing with {} processor, traceId: {}",
            useCase.implementationName(), traceId);

        useCase.executePendingDocuments()
            .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                result.getCorrelationId(), result.getStatus()))
            .doOnError(error -> log.error("Processing failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove(RestApiConstants.MDC_TRACE_ID))
            .subscribe();

        return ServerResponse.accepted()
            .bodyValue(new AsyncProcessResponse(
                RestApiConstants.STATUS_PROCESSING,
                RestApiConstants.MSG_PROCESSING,
                null,
                traceId,
                null,
                Instant.now(),
                null,
                true
            ));
    }

    private AbstractProcessDocumentsUseCase resolveUseCase(String processorType) {
        return switch (processorType.toLowerCase()) {
            case RestApiConstants.PROCESSOR_S3 -> {
                if (s3DocumentUseCase == null) {
                    throw new IllegalStateException(RestApiConstants.MSG_S3_NOT_AVAILABLE);
                }
                yield s3DocumentUseCase;
            }
            case RestApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
            default -> {
                log.warn(RestApiConstants.MSG_UNKNOWN_PROCESSOR, processorType);
                yield soapDocumentUseCase;
            }
        };
    }

    public record AsyncProcessResponse(
        String status,
        String message,
        String correlationId,
        String traceId,
        String externalReference,
        Instant processedAt,
        String errorCode,
        boolean success
    ) {}
}