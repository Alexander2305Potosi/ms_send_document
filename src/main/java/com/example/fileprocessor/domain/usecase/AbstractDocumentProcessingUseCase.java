package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistory;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Base use case for document processing.
 * Refactored: DocumentHistory is now the main information carrier.
 * Processing is STRICTLY SEQUENTIAL to protect external endpoints (like SOAP).
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

    /**
     * Executes the processing of all pending documents.
     * Uses concatMap to ensure one-by-one sequential processing.
     */
    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return Mono.deferContextual(ctx -> {
            String traceId = extractTraceId(ctx);
            LOGGER.info(() -> String.format("[TraceID: %s] Starting SEQUENTIAL execution for: %s", traceId, implementationName()));
            return Mono.just(traceId);
        }).flatMapMany(traceId -> 
            persistencePort.findPendingDocumentsToday(implementationName(), startOfDay)
                // concatMap is used instead of flatMap to ensure synchronous/sequential behavior (one by one)
                .concatMap(doc -> processWithTracking(doc, traceId))
        );
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc, String traceId) {
        final DocumentHistory history = DocumentHistory.fromDocument(doc);
        
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .filter(rows -> rows > 0)
            .doOnDiscard(Long.class, unused -> 
                LOGGER.warning(() -> String.format("[TraceID: %s] Document %s is already being processed or locked.", traceId, doc.getDocumentId()))
            )
            .flatMap(unused -> fetchAndValidate(history))
            .flatMap(h -> uploadDocument(h, doc.getId()))
            .onErrorResume(error -> handleGlobalError(error, doc, traceId))
            .flatMap(response -> finalizeProcessing(doc, history, response, traceId));
    }

    private Mono<DocumentHistory> fetchAndValidate(DocumentHistory history) {
        return productRestGateway.getDocument(history.getProductId(), history.getBusinessDocumentId())
            .map(file -> {
                history.setContent(file.getContent());
                history.setSize(file.getSize());
                history.setContentType(file.getContentType());
                history.setFilename(file.getFilename());
                history.setOrigin(file.getOrigin());
                history.setPais(file.getPais());
                return history;
            })
            .flatMapMany(this::decompress)
            .concatMap(h -> documentValidator.validate(h, true))
            .next()
            .onErrorResume(ProcessingException.class, e -> {
                if (ProcessingResultCodes.isBusinessRule(e.getErrorCode())) {
                    return Mono.error(e);
                }
                return Mono.error(new ProcessingException(e.getErrorCode(), "Validation failed: " + e.getMessage(), e));
            });
    }

    private Mono<FileUploadResponse> finalizeProcessing(Document doc, DocumentHistory history, FileUploadResponse response, String traceId) {
        String nextState = calculateNextState(doc, response);
        String logPrefix;

        if (response.isSuccess()) {
            logPrefix = ProcessingResultCodes.SUCCESS.name();
        } else if (ProcessingResultCodes.PENDING.name().equals(nextState)) {
            logPrefix = ProcessingResultCodes.RETRYABLE_ERROR.name();
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
            .stackTrace(response.getStackTrace())
            .startedAt(history.getStartedAt())
            .completedAt(Instant.now())
            .build();

        return persistencePort.finalizeProcessingAtomically(doc, historyDTO)
            .thenReturn(response);
    }

    private String calculateNextState(Document doc, FileUploadResponse response) {
        if (response.isSuccess()) {
            return ProcessingResultCodes.PROCESSED.name();
        }
        
        int currentRetry = doc.getRetryCountSafe();
        boolean isRetryable = ProcessingResultCodes.isTransient(response.getErrorCode()) && currentRetry < MAX_RETRIES;

        if (isRetryable) {
            doc.setRetryCount(currentRetry + 1);
            return ProcessingResultCodes.PENDING.name();
        }
        
        return ProcessingResultCodes.FAILED.name();
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
            .message(message != null ? message : ProcessingResultCodes.UNKNOWN_ERROR.value())
            .stackTrace(getStackTraceAsString(error))
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private Flux<DocumentHistory> decompress(DocumentHistory history) {
        if (!history.isZip() || history.getFilename() == null || history.getFilename().isBlank()) {
            return Flux.just(history);
        }
        return ZipDecompressor.decompress(history);
    }

    private String extractTraceId(ContextView ctx) {
        return ctx.getOrDefault(TRACE_KEY, DEFAULT_TRACE);
    }

    protected abstract Mono<FileUploadResponse> uploadDocument(DocumentHistory history, Long docId);

    protected abstract String implementationName();
}
