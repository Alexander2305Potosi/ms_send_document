package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.CommunicationLog;
import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ResilienceOperator;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class implementing the Template Method pattern for document processing.
 * Centralizes shared orchestration: claim, preValidate, sendWithResilience, checkpoint, postProcess.
 *
 * <p>Subclasses implement only:
 * <ul>
 *   <li>{@link #validateDocument(ProductDocumentToProcess, String)} - gateway-specific validation</li>
 *   <li>{@link #buildRequest(ProductDocumentToProcess, String)} - gateway-specific request building</li>
 *   <li>{@link #implementationName()} - processor identification for logging</li>
 * </ul>
 */
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ProductDocumentRepository documentRepository;
    protected final ProductStatusAggregator statusAggregator;
    protected final ResilienceOperator resilienceOperator;
    protected final FileGateway fileGateway;
    protected final CommunicationLogRepository logRepository;
    protected final FileValidator fileValidator;
    protected final DocumentValidationRules validationRules;
    protected final FolderExclusionRegexConfig folderExclusionRegex;
    protected final CommunicationLogFactory logFactory;

    protected AbstractDocumentProcessingUseCase(
            ProcessingDependencies deps,
            ResilienceOperator resilienceOperator,
            FileValidator fileValidator,
            DocumentValidationRules validationRules,
            FolderExclusionRegexConfig folderExclusionRegex,
            CommunicationLogFactory logFactory) {
        this.documentRepository = deps.documentRepository();
        this.statusAggregator = deps.statusAggregator();
        this.resilienceOperator = resilienceOperator;
        this.fileGateway = deps.fileGateway();
        this.logRepository = deps.logRepository();
        this.fileValidator = fileValidator;
        this.validationRules = validationRules;
        this.folderExclusionRegex = folderExclusionRegex;
        this.logFactory = logFactory;
    }

    // ============ TEMPLATE METHOD (final - do not override) ============

    /**
     * Entry point - orchestrates processing of all pending documents.
     * This method is final to ensure the template algorithm is not modified.
     */
    @Timed("document.processing")
    public final Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument, maxConcurrency())
            .doOnNext(r -> log.info("Document processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()));
    }

    /**
     * Template method for processing a single document.
     * Defines the algorithm skeleton. Variations are handled by abstract methods.
     */
    protected final Mono<FileUploadResult> processDocument(ProductDocumentToProcess pending, String traceId) {
        return Mono.just(pending)
            .flatMap(doc -> preValidate(doc, traceId))
            .switchIfEmpty(Mono.defer(() ->
                validateDocument(pending, traceId)
                    .flatMap(validDoc -> buildRequest(validDoc, traceId))
                    .flatMap(this::sendWithResilience)
                    .flatMap(result -> checkpoint(pending, result, traceId))
                    .flatMap(result -> postProcess(pending, result, traceId))
            ))
            .doOnNext(result -> log.info("Document {} processed: correlationId={}",
                pending.getFilename(), result.getCorrelationId()));
    }

    // ============ HOOK (can be overridden, default implementation provided) ============

    /**
     * Pre-validation with shared business rules (folder skip, origin, size).
     * Returns Mono.just(FileUploadResult) to short-circuit if document should be skipped.
     * Returns Mono.empty() to continue with normal processing.
     */
    protected Mono<FileUploadResult> preValidate(ProductDocumentToProcess pending, String traceId) {
        // 1. Legacy folder skip (String.contains)
        if (validationRules.shouldSkipFolder(pending.getOrigin())) {
            return skipDocument(pending, traceId, DocumentStatus.SKIPPED.name(),
                ProcessingMessages.MSG_SKIPPED_FOLDER + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER);
        }

        // 2. Regex-based folder exclusion
        if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
            return skipDocument(pending, traceId, DocumentStatus.SKIPPED.name(),
                "Folder excluded by regex: " + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER);
        }

        // 3. Origin pattern check
        if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
            return skipDocument(pending, traceId, DocumentStatus.NOT_SENT.name(),
                ProcessingMessages.MSG_NOT_SENT_ORIGIN + pending.getOrigin(),
                ProcessingResultCodes.NOT_SENT_ORIGIN);
        }

        // 4. Size check
        long fileSize = pending.getContent() != null ? pending.getContent().length : 0;
        if (validationRules.shouldNotSendBySize(fileSize)) {
            return skipDocument(pending, traceId, DocumentStatus.NOT_SENT.name(),
                ProcessingMessages.MSG_SIZE_EXCEEDED + fileSize + ProcessingMessages.MSG_SIZE_EXCEEDED_SUFFIX,
                ProcessingResultCodes.SIZE_EXCEEDED);
        }

        return Mono.empty();
    }

    // ============ ABSTRACT METHODS (must be implemented by subclasses) ============

    /**
     * Validates document according to gateway-specific rules.
     * @param pending document to validate
     * @param traceId trace identifier
     * @return Mono with validated document or error
     */
    protected abstract Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId);

    /**
     * Builds the gateway-specific DocumentSendRequest.
     * @param validDoc validated document
     * @param traceId trace identifier
     * @return Mono with built request
     */
    protected abstract Mono<DocumentSendRequest> buildRequest(
            ProductDocumentToProcess validDoc, String traceId);

    /**
     * Returns the processor implementation name for logging.
     */
    protected abstract String implementationName();

    /**
     * Public accessor for the implementation name.
     */
    public String getImplementationName() {
        return implementationName();
    }

    // ============ SHARED CONCRETE METHODS ============

    /**
     * Sends document with resilience patterns (circuit breaker, retry).
     * Logs both success and failure to CommunicationLog.
     */
    protected Mono<FileUploadResult> sendWithResilience(DocumentSendRequest request) {
        Instant start = Instant.now();

        return Mono.defer(() -> {
            @SuppressWarnings("unchecked")
            Mono<FileUploadResult> decorated = (Mono<FileUploadResult>) resilienceOperator.decorate(
                fileGateway.send(request), request.getTraceId());

            return decorated
                .flatMap(result -> saveCommunicationLog(request, result, 0, start)
                    .thenReturn(result))
                .onErrorResume(error -> {
                    int retries = extractRetryCount(error);
                    String errorCode = extractErrorCode(error);
                    FileUploadResult failureResult = buildFailureResult(errorCode, request.getTraceId());
                    return saveCommunicationLog(request, failureResult, retries, start)
                        .then(Mono.error(error));
                });
        });
    }

    /**
     * Immediate checkpoint - updates document status atomically.
     * This is the critical step that must happen right after send.
     */
    protected Mono<FileUploadResult> checkpoint(
            ProductDocumentToProcess pending, FileUploadResult result, String traceId) {
        String status = result.getStatus();
        String correlationId = DocumentStatus.SUCCESS.name().equals(status)
            ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS.name().equals(status)
            ? null : result.getErrorCode();

        return documentRepository.updateStatus(
                pending.getDocumentId(), status, traceId, correlationId, errorCode)
            .thenReturn(result);
    }

    /**
     * Post-processing - updates product status aggregation.
     * This is deferrable and should not block the main flow.
     */
    protected Mono<FileUploadResult> postProcess(
            ProductDocumentToProcess pending, FileUploadResult result, String traceId) {
        return statusAggregator.updateProductStatus(pending.getProductId(), traceId)
            .thenReturn(result);
    }

    /**
     * Saves communication log for both success and failure outcomes.
     */
    protected Mono<Void> saveCommunicationLog(
            DocumentSendRequest request, FileUploadResult result, int retryCount, Instant startTime) {
        CommunicationLog logEntry = logFactory.create(request, result, retryCount, startTime, Map.of());
        return logRepository.save(logEntry).then();
    }

    /**
     * Skips a document and updates its status.
     */
    protected Mono<FileUploadResult> skipDocument(
            ProductDocumentToProcess pending, String traceId,
            String status, String message, String errorCode) {
        log.info("Document {} skipped: status={}, errorCode={}",
            pending.getDocumentId(), status, errorCode);

        return documentRepository.updateStatus(
                pending.getDocumentId(), status, traceId, null, errorCode)
            .flatMap(v -> statusAggregator.updateProductStatus(pending.getProductId(), traceId))
            .thenReturn(FileUploadResult.builder()
                .status(status)
                .message(message)
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(true)
                .build());
    }

    /**
     * Returns max concurrency for parallel processing.
     * Default is 10, can be overridden by subclasses.
     */
    protected int maxConcurrency() {
        return ProcessingMessages.DEFAULT_MAX_CONCURRENCY;
    }

    // ============ PRIVATE HELPERS ============

    private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
        String traceId = UUID.randomUUID().toString();
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> processDocument(pending, traceId))
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("Document {} claimed by another instance", pending.getDocumentId());
                return Mono.empty();
            }));
    }

    private int extractRetryCount(Throwable error) {
        // Infer retry count from exception if available
        return 0;
    }

    private String extractErrorCode(Throwable error) {
        if (error instanceof com.example.fileprocessor.domain.exception.CommunicationException ce) {
            return ce.getErrorCode();
        }
        return ProcessingResultCodes.UNKNOWN_ERROR;
    }

    private FileUploadResult buildFailureResult(String errorCode, String traceId) {
        return FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .traceId(traceId)
            .processedAt(Instant.now())
            .success(false)
            .build();
    }
}
