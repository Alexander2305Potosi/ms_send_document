package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistory;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ExceptionUtils;
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
    /**
     * Executes the processing of all pending documents for the specific implementation.
     * Documents are processed sequentially to prevent overloading external systems.
     *
     * @return a Flux of FileUploadResponse containing the result of each processed document.
     */
    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return Mono.deferContextual(ctx -> {
            String traceId = extractTraceId(ctx);
            LOGGER.info(() -> String.format("[TraceID: %s] Starting SEQUENTIAL execution for: %s", traceId, implementationName()));
            return Mono.just(traceId);
        }).flatMapMany(traceId -> 
            persistencePort.findPendingDocumentsToday(implementationName(), startOfDay)
                .concatMap(doc -> processWithTracking(doc, traceId))
        );
    }

    /**
     * Orchestrates the tracking and processing of a single document.
     * Locks the document, executes the upload, and finalizes the state.
     *
     * @param doc the document to process.
     * @param traceId the trace identifier for logging.
     * @return a Mono of FileUploadResponse.
     */
    private Mono<FileUploadResponse> processWithTracking(Document doc, String traceId) {
        final DocumentHistory history = DocumentHistory.fromDocument(doc);
        
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .filter(rows -> rows > 0)
            .doOnDiscard(Long.class, unused -> 
                LOGGER.warning(() -> String.format("[TraceID: %s] Document %s is already being processed or locked.", traceId, doc.getDocumentId()))
            )
            .flatMap(unused -> fetchAndValidate(history))
            .flatMap(h -> uploadDocument(h, doc.getId()))
            .onErrorResume(this::handleGlobalError)
            .flatMap(response -> finalizeProcessing(doc, history, response, traceId));
    }

    /**
     * Fetches the document content from the source REST API and performs business validations.
     * Supports ZIP decompression if applicable.
     *
     * @param history the document history object carrying the metadata.
     * @return a Mono containing the validated document history with content.
     */
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

    /**
     * Finalizes the processing of a document by updating its state and saving the audit history.
     *
     * @param doc the document being processed.
     * @param history the document metadata.
     * @param response the result of the upload operation.
     * @param traceId the trace identifier for logging.
     * @return a Mono of FileUploadResponse.
     */
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

    /**
     * Determines the next state of the document based on the upload result and retry logic.
     *
     * @param doc the current document entity.
     * @param response the upload response.
     * @return the name of the next state (PROCESSED, PENDING, or FAILED).
     */
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

    /**
     * Maps unexpected exceptions to a standard FileUploadResponse.
     *
     * @param error the throwable to handle.
     * @return a Mono of FileUploadResponse representing the failure.
     */
    private Mono<FileUploadResponse> handleGlobalError(Throwable error) {
        String errorCode = ProcessingResultCodes.UNKNOWN_ERROR.name();
        String message = error.getMessage();

        if (error instanceof ProcessingException pe) {
            errorCode = pe.getErrorCode();
        }

        return Mono.just(FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .errorCode(errorCode)
            .message(message != null ? message : ProcessingResultCodes.UNKNOWN_ERROR.value())
            .stackTrace(ExceptionUtils.getStackTraceAsString(error))
            .processedAt(Instant.now())
            .success(false)
            .build());
    }



    /**
     * Decompresses ZIP files into individual document history objects.
     *
     * @param history the source document (potentially a ZIP).
     * @return a Flux of DocumentHistory (one for each file inside the ZIP, or the original if not a ZIP).
     */
    private Flux<DocumentHistory> decompress(DocumentHistory history) {
        if (!history.isZip() || history.getFilename() == null || history.getFilename().isBlank()) {
            return Flux.just(history);
        }
        return ZipDecompressor.decompress(history);
    }

    /**
     * Extracts the trace ID from the Reactor Context.
     *
     * @param ctx the context view.
     * @return the trace ID or a default value if not found.
     */
    private String extractTraceId(ContextView ctx) {
        return ctx.getOrDefault(TRACE_KEY, DEFAULT_TRACE);
    }

    /**
     * Abstract method to be implemented by specific providers (S3, SOAP, etc.) to handle the actual upload.
     *
     * @param history the document metadata and content.
     * @param docId the database ID of the document.
     * @return a Mono of FileUploadResponse.
     */
    protected abstract Mono<FileUploadResponse> uploadDocument(DocumentHistory history, Long docId);

    /**
     * Provides the name of the implementation for logging and persistence purposes.
     *
     * @return the implementation name (e.g., "S3", "SOAP").
     */
    protected abstract String implementationName();
}
