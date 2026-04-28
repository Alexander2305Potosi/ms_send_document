package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Handler for product-related REST endpoints.
 */
@Component
public class ProductHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductHandler.class);

    private final LoadProductsUseCase loadProductsUseCase;
    private final AbstractDocumentProcessingUseCase soapDocumentUseCase;
    private final Optional<AbstractDocumentProcessingUseCase> s3DocumentUseCase;

    public ProductHandler(LoadProductsUseCase loadProductsUseCase,
                         AbstractDocumentProcessingUseCase soapDocumentUseCase,
                         Optional<AbstractDocumentProcessingUseCase> s3DocumentUseCase) {
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

        AbstractDocumentProcessingUseCase useCase = resolveUseCase(processorType);
        log.info("Starting async pending product documents processing with {} processor, traceId: {}",
            useCase.getImplementationName(), traceId);

        return ServerResponse.accepted()
            .bodyValue(useCase.executePendingDocuments()
                .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                    result.getCorrelationId(), result.getStatus()))
                .doOnError(error -> log.error("Processing failed for traceId {}: {}", traceId, error.getMessage()))
                .collectList()
            );
    }

    private AbstractDocumentProcessingUseCase resolveUseCase(String processorType) {
        return switch (processorType.toLowerCase()) {
            case ApiConstants.PROCESSOR_S3 -> {
                if (s3DocumentUseCase.isEmpty()) {
                    throw new IllegalStateException(ApiConstants.MSG_S3_NOT_AVAILABLE);
                }
                yield s3DocumentUseCase.get();
            }
            case ApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
            default -> {
                log.warn(ApiConstants.MSG_UNKNOWN_PROCESSOR, processorType);
                yield soapDocumentUseCase;
            }
        };
    }

    private static String resolveTraceId(ServerRequest request) {
        String header = request.headers().firstHeader(ApiConstants.HEADER_TRACE_ID);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}