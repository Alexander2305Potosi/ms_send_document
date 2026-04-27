package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.entity.AsyncOperationStatus;
import com.example.fileprocessor.domain.usecase.AbstractProcessDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentUseCase;
import com.example.fileprocessor.domain.port.out.AsyncOperationRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.RestApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Handler for product-related REST endpoints.
 * All operations are asynchronous - they return immediately while processing continues in background.
 */
@Component
public class ProductHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductHandler.class);

    private final LoadProductsUseCase loadProductsUseCase;
    private final SoapDocumentUseCase soapDocumentUseCase;
    private final S3DocumentUseCase s3DocumentUseCase;
    private final AsyncOperationRepository asyncOperationRepository;

    public ProductHandler(LoadProductsUseCase loadProductsUseCase,
                         SoapDocumentUseCase soapDocumentUseCase,
                         S3DocumentUseCase s3DocumentUseCase,
                         AsyncOperationRepository asyncOperationRepository) {
        this.loadProductsUseCase = loadProductsUseCase;
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCase = s3DocumentUseCase;
        this.asyncOperationRepository = asyncOperationRepository;
    }

    public Mono<ServerResponse> loadProducts(ServerRequest request) {
        String headerTraceId = request.headers().firstHeader(RestApiConstants.HEADER_TRACE_ID);
        final String traceId = (headerTraceId != null && !headerTraceId.isBlank())
            ? headerTraceId
            : UUID.randomUUID().toString();

        log.info("Starting async products load from REST API, traceId: {}", traceId);

        AsyncOperationStatus initialStatus = AsyncOperationStatus.startLoading(traceId);

        return asyncOperationRepository.save(initialStatus)
            .then(Mono.fromRunnable(() -> {
                final String currentTraceId = traceId;
                loadProductsUseCase.execute()
                    .doOnNext(result -> {
                        log.info("Product loaded: {} -> {} ({} documents)",
                            result.getProductId(), result.getStatus(), result.getDocumentCount());
                    })
                    .doOnError(error -> log.error("Load failed for traceId {}: {}", currentTraceId, error.getMessage()))
                    .subscribe();
            }))
            .thenReturn(initialStatus)
            .flatMap(status -> ServerResponse.accepted().bodyValue(status));
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        String processorType = request.queryParam(RestApiConstants.PARAM_PROCESSOR).orElse(RestApiConstants.PROCESSOR_SOAP);

        String headerTraceId = request.headers().firstHeader(RestApiConstants.HEADER_TRACE_ID);
        final String traceId = (headerTraceId != null && !headerTraceId.isBlank())
            ? headerTraceId
            : UUID.randomUUID().toString();

        AbstractProcessDocumentsUseCase useCase = resolveUseCase(processorType);
        log.info("Starting async pending product documents processing with {} processor, traceId: {}",
            useCase.implementationName(), traceId);

        AsyncOperationStatus initialStatus = AsyncOperationStatus.startProcessing(traceId);

        return asyncOperationRepository.save(initialStatus)
            .then(Mono.fromRunnable(() -> {
                final String currentTraceId = traceId;
                useCase.executePendingDocuments()
                    .flatMap(result -> {
                        log.info("Document processed: correlationId={}, status={}",
                            result.getCorrelationId(), result.getStatus());
                        return asyncOperationRepository.findByTraceId(currentTraceId)
                            .flatMap(current -> asyncOperationRepository.updateProgress(
                                currentTraceId,
                                current.getProcessedItems() + 1,
                                current.getSuccessItems() + (result.isSuccess() ? 1 : 0),
                                current.getFailedItems() + (result.isSuccess() ? 0 : 1)
                            ));
                    })
                    .doOnError(error -> log.error("Processing failed for traceId {}: {}", currentTraceId, error.getMessage()))
                    .doFinally(signal -> asyncOperationRepository.markCompleted(currentTraceId).subscribe())
                    .subscribe();
            }))
            .thenReturn(initialStatus)
            .flatMap(status -> ServerResponse.accepted().bodyValue(status));
    }

    public Mono<ServerResponse> getOperationStatus(ServerRequest request) {
        String traceId = request.pathVariable("traceId");

        return asyncOperationRepository.findByTraceId(traceId)
            .flatMap(status -> ServerResponse.ok().bodyValue(status))
            .switchIfEmpty(ServerResponse.notFound().build());
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
}