package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base use case for document processing.
 * Refactored: DocumentHistoryDTO is now the main information carrier.
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

    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return Mono.deferContextual(ctx -> {
            String traceId = extractTraceId(ctx);
            LOGGER.log(Level.INFO, "[TraceID: {0}] Starting SEQUENTIAL execution for: {1}", 
                    new Object[]{traceId, implementationName()});
            return Mono.just(traceId);
        }).flatMapMany(traceId -> persistencePort.findPendingDocumentsToday(implementationName(), startOfDay)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(doc -> processWithTracking(doc, traceId)));
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc, String traceId) {
        final DocumentHistoryDTO historyDto = DocumentHistoryDTO.fromDocument(doc);

        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
                .filter(rows -> rows > 0)
                .doOnDiscard(Long.class,
                        unused -> LOGGER.log(Level.WARNING, "[TraceID: {0}] Document {1} is already being processed or locked.",
                                new Object[]{traceId, doc.getDocumentId()}))
                .flatMap(unused -> fetchAndValidate(historyDto))
                .flatMapMany(h -> uploadDocument(h, doc.getId())
                        .map(resp -> (Boolean.TRUE.equals(doc.getIsZip()) && resp.getFilename() == null) ? resp.toBuilder().filename(h.getFilename()).build() : resp))
                .onErrorResume(error -> handleGlobalError(error).flux())
                .concatMap(response -> {
                    if (response.isTechnicalRetry()) {
                        return saveAuditOnly(doc, historyDto, response, traceId).thenReturn(response);
                    } else {
                        return finalizeProcessing(doc, historyDto, response, traceId);
                    }
                })
                .last();
    }

    private Mono<DocumentHistoryDTO> fetchAndValidate(DocumentHistoryDTO history) {
        return productRestGateway.getDocument(history.getProductId(), history.getBusinessDocumentId())
                .map(file -> history.toBuilder()
                        .content(file.getContent())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .filename(file.getFilename())
                        .originFolder(file.getOriginFolder())
                        .originCountry(file.getOriginCountry())
                        .isZip(file.getIsZip())
                        .build())
                .flatMapMany(this::decompress)
                .concatMap(h -> documentValidator.validate(h, true)
                    .onErrorMap(e -> {
                        if (e instanceof ProcessingException pe && Boolean.TRUE.equals(history.getIsZip())) {
                            pe.setFilename(h.getFilename());
                        }
                        return e;
                    }))
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    if (Boolean.TRUE.equals(history.getIsZip())) {
                        return Mono.error(new ProcessingException(
                            ProcessingResultCodes.EMPTY_CONTENT.value(),
                            ProcessingResultCodes.EMPTY_CONTENT.name()));
                    }
                    return Mono.empty();
                }))
                .onErrorResume(ProcessingException.class, e -> {
                    if (ProcessingResultCodes.isBusinessRule(e.getErrorCode())) {
                        return Mono.error(e);
                    }
                    return Mono.error(
                            new ProcessingException("Validation failed: " + e.getMessage(), e.getErrorCode(), e));
                });
    }

    private Mono<FileUploadResponse> finalizeProcessing(Document doc, DocumentHistoryDTO history,
            FileUploadResponse response, String traceId) {
        int businessRetryCount = doc.getRetryCountSafe();
        String nextState = calculateNextState(doc, response);
        String logPrefix = response.isSuccess() ? ProcessingResultCodes.SUCCESS.name() : 
                         (ProcessingResultCodes.PENDING.name().equals(nextState) ? ProcessingResultCodes.RETRYABLE_ERROR.name() : ProcessingResultCodes.FAILURE.name());

        doc.setState(nextState);
        doc.setSyncMessage(response.getMessage());

        LOGGER.log(Level.INFO, "[TraceID: {0}] [{1}] Document {2} (Product: {3}) -> {4}. Message: {5}",
                new Object[]{traceId, logPrefix, doc.getDocumentId(), doc.getProductId(), nextState, response.getMessage()});

        return persistencePort.finalizeProcessingAtomically(syncHistoryDTO(doc, history, response, nextState, businessRetryCount))
                .thenReturn(response);
    }

    private Mono<Void> saveAuditOnly(Document doc, DocumentHistoryDTO history, FileUploadResponse response, String traceId) {
        if (response.isTechnicalRetry()) {
            LOGGER.log(Level.INFO, "[TraceID: {0}] Recording technical retry audit for Document {1} (Attempt {2})",
                    new Object[]{traceId, doc.getDocumentId(), response.getAttemptCount()});
        }
        return persistencePort.saveHistory(syncHistoryDTO(doc, history, response, doc.getState(), doc.getRetryCountSafe()));
    }

    private DocumentHistoryDTO syncHistoryDTO(Document doc, DocumentHistoryDTO history, FileUploadResponse response, String state, int businessRetryCount) {
        String actualFilename = response.getFilename() != null ? response.getFilename() : 
                        (Boolean.TRUE.equals(doc.getIsZip()) ? history.getFilename() : null);

        String finalMessage = (response.getSyncStatus() != null && !response.getSyncStatus().isBlank())
                ? String.format("[%s] %s", response.getSyncStatus(), response.getMessage())
                : response.getMessage();

        int realRetries = businessRetryCount + (response.getAttemptCount() > 0 ? response.getAttemptCount() - 1 : 0);

        return history.toBuilder()
                .documentId(doc.getId())
                .state(state)
                .useCase(doc.getUseCase())
                .retryCount(realRetries)
                .filename(actualFilename)
                .syncStatus(response.getSyncStatus())
                .syncMessage(finalMessage)
                .completedAt(Instant.now())
                .build();
    }

    private String calculateNextState(Document doc, FileUploadResponse response) {
        if (response.isSuccess()) {
            return ProcessingResultCodes.PROCESSED.name();
        }

        int currentRetry = doc.getRetryCountSafe();
        boolean isRetryable = ProcessingResultCodes.isTransient(response.getSyncStatus()) && currentRetry < MAX_RETRIES;

        if (isRetryable) {
            doc.setRetryCount(currentRetry + 1);
            return ProcessingResultCodes.PENDING.name();
        }

        return ProcessingResultCodes.FAILED.name();
    }

    private Mono<FileUploadResponse> handleGlobalError(Throwable error) {
        String syncStatus = ProcessingResultCodes.UNKNOWN_ERROR.name();
        String message = error.getMessage();
        String filename = null;

        if (error instanceof ProcessingException pe) {
            syncStatus = pe.getErrorCode();
            filename = pe.getFilename();
        }

        return Mono.just(FileUploadResponse.builder()
                .status(ProcessingResultCodes.FAILURE.name())
                .syncStatus(syncStatus)
                .message(message != null ? message : ProcessingResultCodes.UNKNOWN_ERROR.value())
                .processedAt(Instant.now())
                .filename(filename)
                .success(false)
                .build());
    }

    private Flux<DocumentHistoryDTO> decompress(DocumentHistoryDTO history) {
        if (!Boolean.TRUE.equals(history.getIsZip()) || history.getFilename() == null || history.getFilename().isBlank()) {
            return Flux.just(history);
        }
        return ZipDecompressor.decompress(history);
    }

    private String extractTraceId(ContextView ctx) {
        return ctx.getOrDefault(TRACE_KEY, DEFAULT_TRACE);
    }

    protected abstract Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId);

    protected abstract String implementationName();
}
