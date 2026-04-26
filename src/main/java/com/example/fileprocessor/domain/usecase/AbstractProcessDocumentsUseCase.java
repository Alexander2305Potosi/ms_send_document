package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ProductStatus;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.helpers.soap.exception.SoapCommunicationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for document processing use cases.
 * Provides shared validation logic, status management, and error handling.
 * Subclasses must implement sendDocument() to define the actual sending mechanism (SOAP, S3, etc.)
 */
public abstract class AbstractProcessDocumentsUseCase {

    protected static final Logger log = LoggerFactory.getLogger(AbstractProcessDocumentsUseCase.class);

    private final ProductDocumentRepository documentRepository;
    private final ProductRepository productRepository;
    private final FileValidator fileValidator;
    private final SoapCommunicationLogRepository logRepository;
    private final DocumentValidationRules validationRules;
    private final CircuitBreaker circuitBreaker;

    protected AbstractProcessDocumentsUseCase(
            ProductDocumentRepository documentRepository,
            ProductRepository productRepository,
            FileValidator fileValidator,
            SoapCommunicationLogRepository logRepository,
            FileValidationConfig validationConfig,
            CircuitBreaker circuitBreaker) {
        this.documentRepository = documentRepository;
        this.productRepository = productRepository;
        this.fileValidator = fileValidator;
        this.logRepository = logRepository;
        this.validationRules = new DocumentValidationRules(validationConfig);
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Constructor por defecto con CircuitBreaker configurado via registry.
     */
    protected AbstractProcessDocumentsUseCase(
            ProductDocumentRepository documentRepository,
            ProductRepository productRepository,
            FileValidator fileValidator,
            SoapCommunicationLogRepository logRepository,
            FileValidationConfig validationConfig) {
        this.documentRepository = documentRepository;
        this.productRepository = productRepository;
        this.fileValidator = fileValidator;
        this.logRepository = logRepository;
        this.validationRules = new DocumentValidationRules(validationConfig);
        this.circuitBreaker = CircuitBreakerRegistry.ofDefaults()
            .circuitBreaker(getImplementationName());
    }

    public Flux<FileUploadResult> executePendingDocuments() {
        log.info("Fetching pending product documents from database...");
        String rootTraceId = UUID.randomUUID().toString();
        MDC.put("traceId", rootTraceId);
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument, DocumentProcessingConstants.DEFAULT_MAX_CONCURRENCY)
            .doOnNext(response -> log.info("Document processed: correlationId={}", response.getCorrelationId()))
            .doOnError(error -> log.error("Error processing document: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"));
    }

    private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> processDocumentClaimed(pending, traceId))
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Document {} already claimed by another process or not pending, skipping",
                    pending.getDocumentId());
                return Mono.empty();
            }))
            .doFinally(signal -> MDC.remove("traceId"));
    }

    private Mono<FileUploadResult> processDocumentClaimed(ProductDocumentToProcess pending, String traceId) {
        log.info("Processing pending document: {}, productId: {}, traceId: {}",
            pending.getDocumentId(), pending.getProductId(), traceId);

        long fileSize = pending.getContent() != null ? pending.getContent().length : 0;

        // Rule 1: Check if folder should be skipped
        if (validationRules.shouldSkipFolder(pending.getOrigin())) {
            log.info("Document {} skipped due to folder rule, origin: {}",
                pending.getDocumentId(), pending.getOrigin());
            return documentRepository.updateStatus(
                    pending.getDocumentId(),
                    DocumentStatus.SKIPPED_VALUE,
                    traceId,
                    null,
                    DocumentErrorCodes.SKIPPED_FOLDER)
                .then(updateProductStatusIfComplete(pending.getProductId(), traceId))
                .thenReturn(buildResult(DocumentStatus.SKIPPED_VALUE,
                    DocumentProcessingConstants.MSG_SKIPPED_FOLDER + pending.getOrigin(),
                    null, traceId, pending.getDocumentId(), true));
        }

        // Rule 2: Check if origin matches required patterns
        if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
            log.info("Document {} NOT SENT due to origin pattern rule, origin: {}",
                pending.getDocumentId(), pending.getOrigin());
            return documentRepository.updateStatus(
                    pending.getDocumentId(),
                    DocumentStatus.NOT_SENT_VALUE,
                    traceId,
                    null,
                    DocumentErrorCodes.NOT_SENT_ORIGIN)
                .then(updateProductStatusIfComplete(pending.getProductId(), traceId))
                .thenReturn(buildResult(DocumentStatus.NOT_SENT_VALUE,
                    DocumentProcessingConstants.MSG_NOT_SENT_ORIGIN + pending.getOrigin(),
                    null, traceId, pending.getDocumentId(), true));
        }

        // Rule 3: Check file size
        if (validationRules.shouldNotSendBySize(fileSize)) {
            log.info("Document {} NOT SENT due to size rule: {} bytes (>= {} MB). Trace: {}",
                pending.getFilename(), fileSize, validationRules.extractFolderInfo(pending.getOrigin()), traceId);
            return documentRepository.updateStatus(
                    pending.getDocumentId(),
                    DocumentStatus.NOT_SENT_VALUE,
                    traceId,
                    null,
                    DocumentErrorCodes.SIZE_EXCEEDED)
                .then(updateProductStatusIfComplete(pending.getProductId(), traceId))
                .thenReturn(buildResult(DocumentStatus.NOT_SENT_VALUE,
                    DocumentProcessingConstants.MSG_SIZE_EXCEEDED + fileSize + DocumentProcessingConstants.MSG_SIZE_EXCEEDED_SUFFIX,
                    null, traceId, pending.getFilename(), true));
        }

        // Build FileData for validation
        FileData fileData = FileData.builder()
            .documentId(pending.getDocumentId())
            .content(pending.getContent())
            .filename(pending.getFilename())
            .size(fileSize)
            .contentType(pending.getContentType())
            .traceId(traceId)
            .build();

        DocumentValidationRules.FolderInfo folderInfo = validationRules.extractFolderInfo(pending.getOrigin());

        return fileValidator.validate(fileData)
            .flatMap(validData -> {
                SoapRequest request = SoapRequest.builder()
                    .documentId(pending.getDocumentId())
                    .fileContent(validData.getContent())
                    .filename(validData.getFilename())
                    .contentType(validData.getContentType())
                    .fileSize(validData.getSize())
                    .traceId(traceId)
                    .parentFolder(folderInfo.parentFolder())
                    .childFolder(folderInfo.childFolder())
                    .build();
                return sendDocumentWithCircuitBreaker(request, traceId, pending.getDocumentId())
                    .flatMap(result -> updateDocumentStatus(pending, result, traceId))
                    .doOnNext(result -> log.info("Document {} sent via {}: correlationId={}",
                        pending.getFilename(), getImplementationName(), result.getCorrelationId()));
            })
            .onErrorResume(error -> handleDocumentError(pending, error, traceId));
    }

    /**
     * Envía el documento usando Circuit Breaker para resiliencia.
     */
    private Mono<DocumentResult> sendDocumentWithCircuitBreaker(SoapRequest request, String traceId, String documentId) {
        return Mono.fromCallable(() -> request)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .flatMap(req -> sendDocument(req))
            .onErrorResume(CallNotPermittedException.class, e -> {
                log.warn("Circuit breaker OPEN for document {}: {}", documentId, DocumentProcessingConstants.MSG_CIRCUIT_BREAKER_OPEN);
                return Mono.error(new SoapCommunicationException(
                    DocumentProcessingConstants.MSG_CIRCUIT_BREAKER_OPEN,
                    DocumentErrorCodes.CIRCUIT_BREAKER_OPEN,
                    traceId, 0));
            });
    }

    // ============== Status Management ==============

    /**
     * Updates document status and recalculates product status.
     * CA-01, CA-02, CA-03: Product status is recalculated after each document update.
     */
    protected Mono<FileUploadResult> updateDocumentStatus(ProductDocumentToProcess document,
                                                          DocumentResult result, String traceId) {
        String status = result.getStatus();
        String soapCorrelationId = DocumentStatus.SUCCESS_VALUE.equals(status) ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS_VALUE.equals(status) ? null : extractErrorCodeFromMessage(result.getMessage());

        return documentRepository.updateStatus(document.getDocumentId(), status, traceId, soapCorrelationId, errorCode)
            .then(updateProductStatusIfComplete(document.getProductId(), traceId))
            .thenReturn(toFileUploadResult(result));
    }

    /**
     * Recalculates product status based on all its documents.
     * Called after each document status change.
     */
    private Mono<Void> updateProductStatusIfComplete(String productId, String traceId) {
        return documentRepository.findByProductId(productId)
            .collectList()
            .flatMap(docs -> {
                ProductStatus newStatus = ProductStatusAggregator.calculateStatus(docs);
                boolean shouldUpdate = docs.stream()
                    .allMatch(doc -> isTerminalStatus(doc.getStatus()));

                if (shouldUpdate) {
                    log.info("All documents for product {} are in terminal state. Updating to: {}",
                        productId, newStatus);
                    return productRepository.updateStatus(productId, newStatus.name(), traceId);
                }
                return Mono.empty();
            });
    }

    private boolean isTerminalStatus(String status) {
        return DocumentStatus.SUCCESS_VALUE.equals(status)
            || DocumentStatus.FAILURE_VALUE.equals(status)
            || DocumentStatus.SKIPPED_VALUE.equals(status)
            || DocumentStatus.NOT_SENT_VALUE.equals(status);
    }

    protected Mono<Void> saveSuccessLog(String documentId, String filename, String traceId, DocumentResult result) {
        SoapCommunicationLog dbLog = SoapCommunicationLog.builder()
            .traceId(traceId)
            .documentId(documentId)
            .status(DocumentStatus.SUCCESS_VALUE)
            .retryCount(DocumentProcessingConstants.DEFAULT_RETRY_COUNT)
            .filename(filename)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }

    protected Mono<Void> saveErrorLog(String documentId, String filename, String traceId, String errorCode, int retries) {
        SoapCommunicationLog dbLog = SoapCommunicationLog.builder()
            .traceId(traceId)
            .documentId(documentId)
            .status(DocumentStatus.FAILURE_VALUE)
            .retryCount(retries)
            .errorCode(errorCode)
            .filename(filename)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }

    // ============== Error Handling ==============

    protected Mono<FileUploadResult> handleDocumentError(ProductDocumentToProcess document,
                                                         Throwable error, String traceId) {
        String errorCode = extractErrorCode(error);
        String status = isRetryableError(error) ? DocumentStatus.RETRY_VALUE : DocumentStatus.FAILURE_VALUE;
        log.error("Failed to process document {}: {} (status={}, errorCode={})",
            document.getDocumentId(), error.getMessage(), status, errorCode);

        int retries = error instanceof SoapCommunicationException sce ? sce.getRetryCount() : 0;
        return saveErrorLog(document.getDocumentId(), document.getFilename(), traceId, errorCode, retries)
            .then(documentRepository.updateStatus(document.getDocumentId(), status, traceId, null, errorCode))
            .then(updateProductStatusIfComplete(document.getProductId(), traceId))
            .thenReturn(buildResult(status, error.getMessage(), null, traceId, document.getDocumentId(), false));
    }

    protected boolean isRetryableError(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            String code = sce.getErrorCode();
            return DocumentErrorCodes.TIMEOUT.equals(code) || DocumentErrorCodes.GATEWAY_TIMEOUT.equals(code);
        }
        String message = error.getMessage();
        if (message == null) return false;
        return message.contains(DocumentErrorCodes.MSG_TIMEOUT) || message.contains(DocumentErrorCodes.MSG_TIMEOUT_TITLE) || message.contains(DocumentErrorCodes.TIMEOUT);
    }

    protected String extractErrorCode(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        return DocumentErrorCodes.UNKNOWN_ERROR;
    }

    protected String extractErrorCodeFromMessage(String message) {
        if (message == null) return DocumentErrorCodes.UNKNOWN_ERROR;
        if (message.contains(DocumentErrorCodes.MSG_TIMEOUT)) return DocumentErrorCodes.TIMEOUT;
        if (message.contains(DocumentErrorCodes.MSG_VALIDATION)) return DocumentErrorCodes.VALIDATION_ERROR;
        return DocumentErrorCodes.UNKNOWN_ERROR;
    }

    // ============== Helper Methods ==============

    protected FileUploadResult buildResult(String status, String message, String correlationId,
                                            String traceId, String externalReference, boolean success) {
        return FileUploadResult.builder()
            .status(status)
            .message(message)
            .correlationId(correlationId)
            .traceId(traceId)
            .processedAt(Instant.now())
            .externalReference(externalReference)
            .success(success)
            .build();
    }

    protected FileUploadResult toFileUploadResult(DocumentResult result) {
        return FileUploadResult.builder()
            .status(result.getStatus())
            .message(result.getMessage())
            .correlationId(result.getCorrelationId())
            .traceId(result.getTraceId())
            .processedAt(result.getProcessedAt())
            .externalReference(result.getExternalReference())
            .success(result.isSuccess())
            .build();
    }

    // ============== Abstract Methods ==============

    protected abstract Mono<DocumentResult> sendDocument(SoapRequest request);

    protected abstract String getImplementationName();

    public String implementationName() {
        return getImplementationName();
    }
}
