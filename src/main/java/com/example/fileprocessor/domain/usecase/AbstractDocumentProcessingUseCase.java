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
import java.util.List;
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
                    new Object[] { traceId, implementationName() });
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
                        unused -> LOGGER.log(Level.WARNING,
                                "[TraceID: {0}] Document {1} is already being processed or locked.",
                                new Object[] { traceId, doc.getDocumentId() }))
                .flatMapMany(unused -> fetchAndValidate(historyDto)
                        .concatMap(h -> uploadDocument(h, doc.getId())
                                .map(resp -> (Boolean.TRUE.equals(doc.getIsZip()) && resp.getFilename() == null)
                                        ? resp.toBuilder().filename(h.getFilename()).build()
                                        : resp)
                                .onErrorResume(error -> handleGlobalError(error))))
                .onErrorResume(error -> handleGlobalError(error).flux())
                .concatMap(response -> saveAuditOnly(doc, historyDto, response, traceId)
                        .thenReturn(response))
                .collectList()
                .flatMap(responses -> {
                    if (responses.isEmpty()) {
                        return Mono.just(FileUploadResponse.builder()
                                .status(ProcessingResultCodes.FAILURE.name())
                                .syncStatus(ProcessingResultCodes.UNKNOWN_ERROR.name())
                                .message("No files processed")
                                .success(false)
                                .build());
                    }

                    List<FileUploadResponse> finalResults = responses.stream()
                            .filter(r -> !r.isTechnicalRetry())
                            .toList();

                    FileUploadResponse overallResponse;
                    if (finalResults.isEmpty()) {
                        overallResponse = responses.get(responses.size() - 1);
                    } else {
                        overallResponse = finalResults.stream()
                                .filter(r -> !r.isSuccess())
                                .findFirst()
                                .orElse(finalResults.get(finalResults.size() - 1));
                    }

                    return finalizeParentDocumentState(doc, overallResponse, traceId);
                });
    }

    private Flux<DocumentHistoryDTO> fetchAndValidate(DocumentHistoryDTO history) {
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
                            ProcessingException pe;
                            if (e instanceof ProcessingException existingPe) {
                                pe = existingPe;
                                if (pe.getErrorCode() == null || pe.getErrorCode().isBlank()) {
                                    pe = new ProcessingException(pe.getMessage(),
                                            ProcessingResultCodes.UNKNOWN_ERROR.name(), pe.getCause());
                                }
                            } else {
                                pe = new ProcessingException(e.getMessage(),
                                        ProcessingResultCodes.UNKNOWN_ERROR.name(), e);
                            }
                            if (Boolean.TRUE.equals(history.getIsZip())) {
                                pe.setFilename(h.getFilename());
                            }
                            return pe;
                        }))
                .switchIfEmpty(Flux.defer(() -> {
                    if (Boolean.TRUE.equals(history.getIsZip())) {
                        return Flux.error(new ProcessingException(
                                ProcessingResultCodes.EMPTY_CONTENT.value(),
                                ProcessingResultCodes.EMPTY_CONTENT.name()));
                    }
                    return Flux.empty();
                }))
                .onErrorResume(ProcessingException.class, e -> {
                    if (ProcessingResultCodes.isBusinessRule(e.getErrorCode())) {
                        return Flux.error(e);
                    }
                    ProcessingException pe = new ProcessingException("Validation failed: " + e.getMessage(),
                            e.getErrorCode(), e);
                    pe.setFilename(e.getFilename());
                    return Flux.error(pe);
                });
    }

    private Mono<FileUploadResponse> finalizeParentDocumentState(Document doc, FileUploadResponse response, String traceId) {
        int businessRetryCount = doc.getRetryCountSafe();
        NextStateResult result = calculateNextState(businessRetryCount, response);
        String logPrefix = response.isSuccess() ? ProcessingResultCodes.SUCCESS.name()
                : (ProcessingResultCodes.PENDING.name().equals(result.state()) ? ProcessingResultCodes.RETRYABLE_ERROR.name()
                        : ProcessingResultCodes.FAILURE.name());

        doc.setState(result.state());
        doc.setRetryCount(result.newRetryCount());
        doc.setSyncMessage(response.getMessage());

        LOGGER.log(Level.INFO, "[TraceID: {0}] [{1}] Finalizing parent Document {2} (Product: {3}) -> {4}. Message: {5}",
                new Object[] { traceId, logPrefix, doc.getDocumentId(), doc.getProductId(), result.state(),
                        response.getMessage() });

        DocumentHistoryDTO parentHistory = DocumentHistoryDTO.builder()
                .documentId(doc.getId())
                .businessDocumentId(doc.getDocumentId())
                .productId(doc.getProductId())
                .state(result.state())
                .useCase(doc.getUseCase())
                .retryCount(calculateEffectiveRetries(businessRetryCount, response))
                .businessRetryCount(result.newRetryCount())
                .filename(doc.isZipSafe() ? doc.getName() : null)
                .syncStatus(response.getSyncStatus())
                .syncMessage(formatSyncMessage(response))
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        return persistencePort.finalizeProcessingAtomically(parentHistory)
                .thenReturn(response);
    }

    private Mono<Void> saveAuditOnly(Document doc, DocumentHistoryDTO history, FileUploadResponse response,
            String traceId) {
        String fileState;
        if (response.isTechnicalRetry()) {
            LOGGER.log(Level.INFO, "[TraceID: {0}] Recording technical retry audit for Document {1} (Attempt {2})",
                    new Object[] { traceId, doc.getDocumentId(), response.getAttemptCount() });
            fileState = ProcessingResultCodes.IN_PROGRESS.name();
        } else {
            fileState = response.isSuccess()
                    ? ProcessingResultCodes.PROCESSED.name()
                    : ProcessingResultCodes.FAILED.name();
        }
        return persistencePort
                .saveHistory(syncHistoryDTO(doc, history, response, fileState, doc.getRetryCountSafe()));
    }

    private DocumentHistoryDTO syncHistoryDTO(Document doc, DocumentHistoryDTO history, FileUploadResponse response,
            String state, int businessRetryCount) {
        String actualFilename = response.getFilename() != null ? response.getFilename()
                : (Boolean.TRUE.equals(doc.getIsZip()) ? history.getFilename() : null);

        return history.toBuilder()
                .documentId(doc.getId())
                .state(state)
                .useCase(doc.getUseCase())
                .retryCount(calculateEffectiveRetries(businessRetryCount, response))
                .businessRetryCount(doc.getRetryCountSafe())
                .filename(actualFilename)
                .syncStatus(response.getSyncStatus())
                .syncMessage(formatSyncMessage(response))
                .completedAt(Instant.now())
                .homologationFolder(response.getHomologationFolder())
                .homologationCountry(response.getHomologationCountry())
                .categoriaHomologada(response.getCategoriaHomologada())
                .build();
    }

    // ── Helper methods ──────────────────────────────────────────────────────────

    private record NextStateResult(String state, int newRetryCount) {}

    private NextStateResult calculateNextState(int currentRetryCount, FileUploadResponse response) {
        if (response.isSuccess()) {
            return new NextStateResult(ProcessingResultCodes.PROCESSED.name(), currentRetryCount);
        }

        if (ProcessingResultCodes.isBusinessRule(response.getSyncStatus()) ||
                (response.getSyncStatus() != null && !ProcessingResultCodes.isTransient(response.getSyncStatus()))) {
            return new NextStateResult(ProcessingResultCodes.BUSINESS_REJECTION.name(), currentRetryCount);
        }

        boolean isRetryable = ProcessingResultCodes.isTransient(response.getSyncStatus()) && currentRetryCount < MAX_RETRIES;
        if (isRetryable) {
            return new NextStateResult(ProcessingResultCodes.PENDING.name(), currentRetryCount + 1);
        }

        return new NextStateResult(ProcessingResultCodes.FAILED.name(), currentRetryCount);
    }

    private int calculateEffectiveRetries(int businessRetryCount, FileUploadResponse response) {
        return businessRetryCount + Math.max(0, response.getAttemptCount() - 1);
    }

    private String formatSyncMessage(FileUploadResponse response) {
        if (response.getSyncStatus() != null && !response.getSyncStatus().isBlank()) {
            return String.format("[%s] %s", response.getSyncStatus(), response.getMessage());
        }
        return response.getMessage();
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
        if (!Boolean.TRUE.equals(history.getIsZip()) || history.getFilename() == null
                || history.getFilename().isBlank()) {
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
