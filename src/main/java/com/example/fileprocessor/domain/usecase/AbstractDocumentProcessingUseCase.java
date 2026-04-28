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
 * Abstract base class for document processing use cases.
 * Handles shared orchestration logic; subclasses implement gateway-specific behavior.
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

    // ============ TEMPLATE METHOD (final) ============

    @Timed("document.processing")
    public final Flux<FileUploadResult> executePendingDocuments() {
        Duration drainTimeout = Duration.ofSeconds(drainTimeoutSeconds());

        return documentRepository.findPendingDocuments()
            .takeWhile(doc -> !isShuttingDown())
            .flatMap(this::processPendingDocument, maxConcurrency())
            .take(drainTimeout)
            .doOnTerminate(() -> log.info("Pipeline {} drained. {} in flight",
                implementationName(), countInFlight()))
            .doOnCancel(() -> log.warn("Pipeline {} force-cancelled due to shutdown timeout",
                implementationName()))
            .doOnNext(r -> log.info("Document processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()));
    }

    private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
        String traceId = UUID.randomUUID().toString();
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> processDocumentInternal(pending, traceId))
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("Document {} claimed by another instance", pending.getDocumentId());
                return Mono.empty();
            }));
    }

    // ============ PROCESS INTERNAL (shared orchestration) ============

    /**
     * Internal orchestration that calls abstract gateway-specific methods.
     * This is the template method - do not override.
     */
    protected final Mono<FileUploadResult> processDocumentInternal(
            ProductDocumentToProcess pending, String traceId) {

        // Step 1: Filter by folder (abstract - implemented by subclass)
        Mono<ProductDocumentToProcess> folderFiltered = filterByFolder(pending, traceId);

        // Step 2: Validate document (abstract - implemented by subclass)
        Mono<ProductDocumentToProcess> validated = folderFiltered
            .flatMap(doc -> validateDocument(doc, traceId));

        // Step 3: Build request (abstract - implemented by subclass)
        Mono<DocumentSendRequest> request = validated
            .flatMap(doc -> buildRequest(doc, traceId));

        // Step 4-6: Shared pipeline (concrete)
        return request
            .flatMap(this::sendWithResilience)
            .flatMap(result -> checkpoint(pending, result, traceId))
            .flatMap(result -> postProcess(pending, result, traceId))
            .doOnNext(result -> log.info("Document {} processed: correlationId={}",
                pending.getFilename(), result.getCorrelationId()));
    }

    // ============ ABSTRACT METHODS (gateway-specific) ============

    /**
     * Filters document based on gateway-specific folder rules.
     * Returns Mono.empty() to continue, Mono.just(result) to skip.
     */
    protected abstract Mono<ProductDocumentToProcess> filterByFolder(
            ProductDocumentToProcess pending, String traceId);

    /**
     * Validates document according to gateway-specific rules.
     */
    protected abstract Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId);

    /**
     * Builds the gateway-specific DocumentSendRequest.
     */
    protected abstract Mono<DocumentSendRequest> buildRequest(
            ProductDocumentToProcess validDoc, String traceId);

    /**
     * Returns the processor implementation name for logging.
     */
    protected abstract String implementationName();

    public String getImplementationName() {
        return implementationName();
    }

    // ============ SHARED CONCRETE METHODS ============

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

    protected Mono<FileUploadResult> postProcess(
            ProductDocumentToProcess pending, FileUploadResult result, String traceId) {
        return statusAggregator.updateProductStatus(pending.getProductId(), traceId)
            .thenReturn(result);
    }

    protected Mono<Void> saveCommunicationLog(
            DocumentSendRequest request, FileUploadResult result, int retryCount, Instant startTime) {
        CommunicationLog logEntry = logFactory.create(request, result, retryCount, startTime, Map.of());
        return logRepository.save(logEntry).then();
    }

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

    protected Mono<ProductDocumentToProcess> skipFolderCheck(
            ProductDocumentToProcess pending, String traceId, String reason) {
        return Mono.just(skipDocument(pending, traceId, DocumentStatus.SKIPPED.name(),
            reason, ProcessingResultCodes.SKIPPED_FOLDER))
            .thenMany(Mono.empty())
            .then(Mono.just(pending)); // Return original to signal skip
    }

    // ============ HOOK METHODS (can be overridden) ============

    protected boolean isShuttingDown() {
        return false;
    }

    protected int drainTimeoutSeconds() {
        return 20;
    }

    protected long countInFlight() {
        return 0;
    }

    protected int maxConcurrency() {
        return ProcessingMessages.DEFAULT_MAX_CONCURRENCY;
    }

    // ============ ERROR HELPERS ============

    private int extractRetryCount(Throwable error) {
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