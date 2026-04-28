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
 * Uses nested strategy classes to group related methods for better readability.
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

    // ============ PRE-VALIDATION HOOK (shared business rules) ============

    protected Mono<FileUploadResult> preValidate(ProductDocumentToProcess pending, String traceId) {
        if (validationRules.shouldSkipFolder(pending.getOrigin())) {
            return skipDocument(pending, traceId, DocumentStatus.SKIPPED.name(),
                ProcessingMessages.MSG_SKIPPED_FOLDER + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER);
        }
        if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
            return skipDocument(pending, traceId, DocumentStatus.SKIPPED.name(),
                "Folder excluded by regex: " + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER);
        }
        if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
            return skipDocument(pending, traceId, DocumentStatus.NOT_SENT.name(),
                ProcessingMessages.MSG_NOT_SENT_ORIGIN + pending.getOrigin(),
                ProcessingResultCodes.NOT_SENT_ORIGIN);
        }
        long fileSize = pending.getContent() != null ? pending.getContent().length : 0;
        if (validationRules.shouldNotSendBySize(fileSize)) {
            return skipDocument(pending, traceId, DocumentStatus.NOT_SENT.name(),
                ProcessingMessages.MSG_SIZE_EXCEEDED + fileSize + ProcessingMessages.MSG_SIZE_EXCEEDED_SUFFIX,
                ProcessingResultCodes.SIZE_EXCEEDED);
        }
        return Mono.empty();
    }

    // ============ ABSTRACT METHODS (must be implemented by subclasses) ============

    protected abstract Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId);

    protected abstract Mono<DocumentSendRequest> buildRequest(
            ProductDocumentToProcess validDoc, String traceId);

    protected abstract String implementationName();

    public String getImplementationName() {
        return implementationName();
    }

    // ============ CONCRETE SHARED METHODS ============

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

    // ============ CLAIMING (private helper) ============

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