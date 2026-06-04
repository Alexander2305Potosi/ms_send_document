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
        DocumentHistoryDTO baseHistory = DocumentHistoryDTO.fromDocument(doc);
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
                .filter(rows -> rows > 0)
                .doOnDiscard(Long.class,
                        unused -> LOGGER.log(Level.WARNING,
                                "[TraceID: {0}] Document {1} is already being processed or locked.",
                                new Object[] { traceId, doc.getDocumentId() }))
                .flatMap(unused -> downloadDocument(baseHistory)
                        .flatMap(masterHistory -> decompressAndValidate(masterHistory)
                                .flatMap(innerFile -> uploadDocument(innerFile, doc.getId())
                                        .map(resp -> ensureFilename(resp, innerFile, doc.getIsZip()))
                                        .flatMap(resp -> saveInnerHistoryIfZip(doc, innerFile, resp)), 1
                                )
                                .collectList()
                                .flatMap(responses -> concludeProcessing(doc, masterHistory, responses, traceId))
                                .onErrorResume(error -> handleGlobalErrorAndConclude(error, doc, masterHistory, traceId))
                        )
                )
                .onErrorResume(error -> handleGlobalErrorAndConclude(error, doc, baseHistory, traceId));
    }

    private Mono<DocumentHistoryDTO> downloadDocument(DocumentHistoryDTO baseHistory) {
        return productRestGateway.getDocument(baseHistory.getProductId(), baseHistory.getBusinessDocumentId())
                .map(file -> baseHistory.toBuilder()
                        .content(file.getContent())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .filename(file.getFilename())
                        .originFolder(file.getOriginFolder())
                        .originCountry(file.getOriginCountry())
                        .isZip(file.getIsZip())
                        .build());
    }

    private Flux<DocumentHistoryDTO> decompressAndValidate(DocumentHistoryDTO masterHistory) {
        return decompress(masterHistory)
                .concatMap(h -> documentValidator.validate(h, true)
                        .onErrorMap(e -> DocumentHistoryFactory.mapValidationError(e, masterHistory, h)))
                .switchIfEmpty(Flux.defer(() -> {
                    if (Boolean.TRUE.equals(masterHistory.getIsZip())) {
                        return Flux.<DocumentHistoryDTO>error(new ProcessingException(
                                ProcessingResultCodes.EMPTY_CONTENT.value(),
                                ProcessingResultCodes.EMPTY_CONTENT.name()));
                    }
                    return Flux.<DocumentHistoryDTO>empty();
                }))
                .onErrorResume(ProcessingException.class, e -> {
                    if (ProcessingResultCodes.isBusinessRule(e.getErrorCode())) {
                        return Flux.error(e);
                    }
                    ProcessingException pe = new ProcessingException("Validation failed: " + e.getMessage(),
                            e.getErrorCode(), e);
                    pe.setFilename(e.getFilename() != null ? e.getFilename() : masterHistory.getFilename());
                    return Flux.error(pe);
                });
    }

    private FileUploadResponse ensureFilename(FileUploadResponse resp, DocumentHistoryDTO innerFile, Boolean isZip) {
        if (Boolean.TRUE.equals(isZip) && resp.getFilename() == null) {
            return resp.toBuilder().filename(innerFile.getFilename()).build();
        }
        return resp;
    }

    private Mono<FileUploadResponse> saveInnerHistoryIfZip(Document doc, DocumentHistoryDTO innerFile, FileUploadResponse resp) {
        if (Boolean.TRUE.equals(doc.getIsZip())) {
            return persistencePort.saveHistory(DocumentHistoryFactory.syncHistoryDTO(doc, innerFile, resp)).thenReturn(resp);
        }
        return Mono.just(resp);
    }

    private Mono<FileUploadResponse> concludeProcessing(Document doc, DocumentHistoryDTO masterHistory, List<FileUploadResponse> responses, String traceId) {
        if (responses.isEmpty()) {
            return handleGlobalErrorAndConclude(
                    new ProcessingException("No files processed", ProcessingResultCodes.EMPTY_CONTENT.name()),
                    doc, masterHistory, traceId);
        }

        FileUploadResponse representative = responses.get(0);
        boolean hasTechnicalRetry = responses.stream().anyMatch(FileUploadResponse::isTechnicalRetry);

        if (hasTechnicalRetry) {
            return saveAuditOnly(doc, masterHistory, responses, traceId).thenReturn(representative);
        } else {
            return finalizeProcessing(doc, masterHistory, responses, traceId).thenReturn(representative);
        }
    }

    private Mono<FileUploadResponse> handleGlobalErrorAndConclude(Throwable error, Document doc, DocumentHistoryDTO masterHistory, String traceId) {
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(error);
        if (error instanceof ProcessingException pe && pe.getFilename() != null) {
            response = response.toBuilder().filename(pe.getFilename()).build();
        }
        final FileUploadResponse finalResponse = response;

        final DocumentHistoryDTO errorFileHistoryDto = finalResponse.getFilename() != null
                ? masterHistory.toBuilder().filename(finalResponse.getFilename()).isZip(false).build()
                : null;

        Mono<Void> saveErrorHistory = Mono.empty();
        if (Boolean.TRUE.equals(doc.getIsZip()) && errorFileHistoryDto != null) {
            saveErrorHistory = persistencePort.saveHistory(
                    DocumentHistoryFactory.syncHistoryDTO(doc, errorFileHistoryDto, finalResponse)
            );
        }

        List<FileUploadResponse> responses = List.of(finalResponse);
        Mono<Void> finalizeMono = finalResponse.isTechnicalRetry()
                ? saveAuditOnly(doc, masterHistory, responses, traceId)
                : finalizeProcessing(doc, masterHistory, responses, traceId);

        return saveErrorHistory.then(finalizeMono).thenReturn(finalResponse);
    }

    private Mono<Void> finalizeProcessing(Document doc, DocumentHistoryDTO history,
            List<FileUploadResponse> responses, String traceId) {
        int currentRetryCount = doc.getRetryCountSafe();
        DocumentHistoryFactory.ProcessingConclusion conclusion = DocumentHistoryFactory.calculateNextState(currentRetryCount, responses);

        String logPrefix;
        if (responses.stream().allMatch(FileUploadResponse::isSuccess)) {
            logPrefix = ProcessingResultCodes.SUCCESS.name();
        } else if (ProcessingResultCodes.PENDING.name().equals(conclusion.nextState())) {
            logPrefix = ProcessingResultCodes.RETRYABLE_ERROR.name();
        } else {
            logPrefix = ProcessingResultCodes.FAILURE.name();
        }

        LOGGER.log(Level.INFO, "[TraceID: {0}] [{1}] Document {2} (Product: {3}) -> {4}. Messages: {5}",
                new Object[] { traceId, logPrefix, doc.getDocumentId(), doc.getProductId(), conclusion.nextState(),
                        DocumentHistoryFactory.aggregateMessages(responses) });

        return persistencePort
                .finalizeProcessingAtomically(DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion))
                .then();
    }

    private Mono<Void> saveAuditOnly(Document doc, DocumentHistoryDTO history, List<FileUploadResponse> responses,
            String traceId) {
        int currentRetryCount = doc.getRetryCountSafe();
        LOGGER.log(Level.INFO, "[TraceID: {0}] Recording technical retry audit for Document {1} (Attempt {2})",
                new Object[] { traceId, doc.getDocumentId(), currentRetryCount });
        if (Boolean.TRUE.equals(history.getIsZip())) {
            return Mono.empty();
        }
        DocumentHistoryFactory.ProcessingConclusion conclusion = new DocumentHistoryFactory.ProcessingConclusion(
                doc.getState(), currentRetryCount);
        return persistencePort
                .saveHistory(DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion));
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