package com.example.fileprocessor.infrastructure.rest.handler;

import com.example.fileprocessor.domain.usecase.AbstractProcessDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProductHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductHandler.class);

    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String MSG_LOADING = "Product loading from REST API started";
    public static final String MSG_PROCESSING = "Pending product documents processing started";

    private final LoadProductsUseCase loadProductsUseCase;
    private final SoapDocumentUseCase soapDocumentUseCase;
    private final S3DocumentUseCase s3DocumentUseCase;

    public ProductHandler(LoadProductsUseCase loadProductsUseCase,
                         SoapDocumentUseCase soapDocumentUseCase,
                         S3DocumentUseCase s3DocumentUseCase) {
        this.loadProductsUseCase = loadProductsUseCase;
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCase = s3DocumentUseCase;
    }

    public Mono<ServerResponse> loadProducts(ServerRequest request) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Starting products load from REST API, traceId: {}", traceId);

        loadProductsUseCase.execute()
            .doOnNext(result -> log.info("Product loaded: {} -> {} ({} documents)",
                result.getProductId(), result.getStatus(), result.getDocumentCount()))
            .doOnError(error -> log.error("Load failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ServerResponse.accepted()
            .bodyValue(new AsyncProcessResponse(
                STATUS_LOADING,
                MSG_LOADING,
                null,
                traceId,
                null,
                Instant.now(),
                null,
                true
            ));
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        String processorType = request.queryParam("processor").orElse(PROCESSOR_SOAP);

        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        AbstractProcessDocumentsUseCase useCase = resolveUseCase(processorType);
        log.info("Starting pending product documents processing with {} processor, traceId: {}",
            useCase.implementationName(), traceId);

        useCase.executePendingDocuments()
            .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                result.getCorrelationId(), result.getStatus()))
            .doOnError(error -> log.error("Processing failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ServerResponse.accepted()
            .bodyValue(new AsyncProcessResponse(
                STATUS_PROCESSING,
                MSG_PROCESSING,
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
            case PROCESSOR_S3 -> s3DocumentUseCase;
            case PROCESSOR_SOAP -> soapDocumentUseCase;
            default -> {
                log.warn("Unknown processor type '{}', defaulting to SOAP", processorType);
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