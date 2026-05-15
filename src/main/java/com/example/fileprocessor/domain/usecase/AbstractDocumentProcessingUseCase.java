package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Base use case for document processing.
 * Clean Architecture compliant: Decoupled orchestration and state management.
 */
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    private static final int MAX_RETRIES = 3;
    private static final String DEFAULT_TRACE = "unknown-trace";
    private static final String TRACE_KEY = ProcessingException.HEADER_TRACE_ID;

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    protected AbstractDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator) {
        this.persistencePort = persistencePort;
        this.productRestGateway = productRestGateway;
        this.documentValidator = documentValidator;
    }

    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return Mono.deferContextual(ctx -> {
            String traceId = extractTraceId(ctx);
            LOGGER.info(() -> String.format("[TraceID: %s] Starting execution for: %s", traceId, implementationName()));
            return Mono.just(traceId);
        }).flatMapMany(traceId -> 
            persistencePort.findPendingDocumentsToday(implementationName(), startOfDay)
                .concatMap(doc -> processWithTracking(doc, traceId))
        );
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc, String traceId) {
        final Instant startTime = Instant.now();
        
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .filter(rows -> rows > 0)
            .doOnDiscard(Long.class, unused -> 
                LOGGER.warning(() -> String.format("[TraceID: %s] Document %s is already being processed or locked.", traceId, doc.getDocumentId()))
            )
            .flatMap(unused -> fetchAndValidate(doc))
            .flatMap(file -> uploadDocument(file, doc.getProductId(), doc.getId()))
            .onErrorResume(error -> handleGlobalError(error, doc, traceId))
            .flatMap(response -> finalizeProcessing(doc, response, startTime, traceId));
    }

    private Mono<ProductDocumentHistory> fetchAndValidate(Document doc) {
        return productRestGateway.getDocument(doc.getProductId(), doc.getDocumentId())
            .map(ProductDocumentHistory::from)
            .flatMapMany(this::decompress)
            .concatMap(file -> documentValidator.validate(file, true))
            .next()
            .onErrorResume(ProcessingException.class, e -> {
                if (ProcessingResultCodes.isBusinessRule(e.getErrorCode())) {
                    return Mono.error(e);
                }
                return Mono.error(new ProcessingException(e.getErrorCode(), "Validation failed: " + e.getMessage(), e));
            });
    }

    private Mono<FileUploadResponse> finalizeProcessing(Document doc, FileUploadResponse response, Instant startTime, String traceId) {
        String nextState = calculateNextState(doc, response);
        String logPrefix;

        if (response.isSuccess()) {
            logPrefix = ProcessingResultCodes.SUCCESS.name();
        } else if (ProductState.PENDING.equals(nextState)) {
            logPrefix = "RETRYABLE_ERROR";
        } else {
            logPrefix = ProcessingResultCodes.FAILURE.name();
        }

        doc.setState(nextState);
        doc.setErrorMessage(response.getMessage());

        LOGGER.info(() -> String.format("[TraceID: %s] [%s] Document %s (Product: %s) -> %s. Message: %s",
                traceId, logPrefix, doc.getDocumentId(), doc.getProductId(), nextState, response.getMessage()));

        DocumentHistoryDTO historyDTO = DocumentHistoryDTO.builder()
            .errorCode(response.getErrorCode())
            .errorMessage(response.getMessage())
            .startedAt(startTime)
            .completedAt(Instant.now())
            .build();

        return persistencePort.finalizeProcessingAtomically(doc, historyDTO)
            .thenReturn(response);
    }

    private String calculateNextState(Document doc, FileUploadResponse response) {
        if (response.isSuccess()) {
            return ProductState.PROCESSED;
        }
        
        int currentRetry = doc.getRetryCountSafe();
        boolean isRetryable = ProcessingResultCodes.isTransient(response.getErrorCode()) && currentRetry < MAX_RETRIES;

        if (isRetryable) {
            doc.setRetryCount(currentRetry + 1);
            return ProductState.PENDING;
        }
        
        return ProductState.FAILED;
    }

    private Mono<FileUploadResponse> handleGlobalError(Throwable error, Document doc, String traceId) {
        String errorCode = ProcessingResultCodes.UNKNOWN_ERROR.name();
        String message = error.getMessage();

        if (error instanceof ProcessingException pe) {
            errorCode = pe.getErrorCode();
        }

        return Mono.just(FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .errorCode(errorCode)
            .message(message != null ? message : "Unexpected processing error")
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    private Flux<ProductDocumentHistory> decompress(ProductDocumentHistory file) {
        if (!file.isZip() || file.getFilename() == null || file.getFilename().isBlank()) {
            return Flux.just(file);
        }
        return ZipDecompressor.decompress(file);
    }

    private String extractTraceId(ContextView ctx) {
        return ctx.getOrDefault(TRACE_KEY, DEFAULT_TRACE);
    }

    protected abstract Mono<FileUploadResponse> uploadDocument(ProductDocumentHistory doc, String productId, Long docId);

    protected abstract String implementationName();
}
