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
import com.example.fileprocessor.domain.exception.CommunicationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * Constructor with CircuitBreaker. Preferred constructor for production.
     */
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

    public Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument, DocumentProcessingConstants.DEFAULT_MAX_CONCURRENCY)
            .doOnNext(response -> log.info("Document processed: correlationId={}", response.getCorrelationId()))
            .doOnError(error -> log.error("Error processing document: {}", error.getMessage()));
    }

    private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
        String traceId = UUID.randomUUID().toString();
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> processDocumentClaimed(pending, traceId))
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Document {} already claimed by another process or not pending, skipping",
                    pending.getDocumentId());
                return Mono.empty();
            }));
    }

    private Mono<FileUploadResult> processDocumentClaimed(ProductDocumentToProcess pending, String traceId) {
        log.info("Processing pending document: {}, productId: {}", pending.getDocumentId(), pending.getProductId());

        long fileSize = pending.getContent() != null ? pending.getContent().length : 0;

        if (validationRules.shouldSkipFolder(pending.getOrigin())) {
            return skipDueToFolderRule(pending, traceId);
        }
        if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
            return skipDueToOriginRule(pending, traceId);
        }
        if (validationRules.shouldNotSendBySize(fileSize)) {
            return skipDueToSizeRule(pending, traceId, fileSize);
        }

        return validateAndSend(pending, traceId, fileSize);
    }

    private Mono<FileUploadResult> skipDueToFolderRule(ProductDocumentToProcess pending, String traceId) {
        log.info("Document {} skipped due to folder rule, origin: {}", pending.getDocumentId(), pending.getOrigin());
        return documentRepository.updateStatus(pending.getDocumentId(), DocumentStatus.SKIPPED_VALUE, traceId, null, DocumentErrorCodes.SKIPPED_FOLDER)
            .then(updateProductStatusIfComplete(pending.getProductId(), traceId))
            .thenReturn(buildSkippedResult(pending.getDocumentId(), DocumentProcessingConstants.MSG_SKIPPED_FOLDER + pending.getOrigin(), DocumentStatus.SKIPPED_VALUE));
    }

    private Mono<FileUploadResult> skipDueToOriginRule(ProductDocumentToProcess pending, String traceId) {
        log.info("Document {} NOT SENT due to origin pattern rule, origin: {}", pending.getDocumentId(), pending.getOrigin());
        return documentRepository.updateStatus(pending.getDocumentId(), DocumentStatus.NOT_SENT_VALUE, traceId, null, DocumentErrorCodes.NOT_SENT_ORIGIN)
            .then(updateProductStatusIfComplete(pending.getProductId(), traceId))
            .thenReturn(buildSkippedResult(pending.getDocumentId(), DocumentProcessingConstants.MSG_NOT_SENT_ORIGIN + pending.getOrigin(), DocumentStatus.NOT_SENT_VALUE));
    }

    private Mono<FileUploadResult> skipDueToSizeRule(ProductDocumentToProcess pending, String traceId, long fileSize) {
        log.info("Document {} NOT SENT due to size rule: {} bytes", pending.getFilename(), fileSize);
        return documentRepository.updateStatus(pending.getDocumentId(), DocumentStatus.NOT_SENT_VALUE, traceId, null, DocumentErrorCodes.SIZE_EXCEEDED)
            .then(updateProductStatusIfComplete(pending.getProductId(), traceId))
            .thenReturn(buildSkippedResult(pending.getFilename(), DocumentProcessingConstants.MSG_SIZE_EXCEEDED + fileSize + DocumentProcessingConstants.MSG_SIZE_EXCEEDED_SUFFIX, DocumentStatus.NOT_SENT_VALUE));
    }

    private Mono<FileUploadResult> validateAndSend(ProductDocumentToProcess pending, String traceId, long fileSize) {
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
            .flatMap(validData -> createAndSendRequest(pending, validData, folderInfo, traceId))
            .onErrorResume(error -> handleDocumentError(pending, error, traceId));
    }

    private Mono<FileUploadResult> createAndSendRequest(ProductDocumentToProcess pending, FileData validData,
                                                        DocumentValidationRules.FolderInfo folderInfo, String traceId) {
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
    }

    private Mono<DocumentResult> sendDocumentWithCircuitBreaker(SoapRequest request, String traceId, String documentId) {
        return Mono.fromCallable(() -> request)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .flatMap(req -> sendDocument(req))
            .onErrorResume(CallNotPermittedException.class, e -> {
                log.warn("Circuit breaker OPEN for document {}: {}", documentId, DocumentProcessingConstants.MSG_CIRCUIT_BREAKER_OPEN);
                return Mono.error(new CommunicationException(
                    DocumentProcessingConstants.MSG_CIRCUIT_BREAKER_OPEN,
                    DocumentErrorCodes.CIRCUIT_BREAKER_OPEN,
                    traceId, 0));
            });
    }

    // ============== Status Management ==============

    protected Mono<FileUploadResult> updateDocumentStatus(ProductDocumentToProcess document,
                                                          DocumentResult result, String traceId) {
        String status = result.getStatus();
        String soapCorrelationId = DocumentStatus.SUCCESS_VALUE.equals(status) ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS_VALUE.equals(status) ? null : extractErrorCodeFromMessage(result.getMessage());

        return documentRepository.updateStatus(document.getDocumentId(), status, traceId, soapCorrelationId, errorCode)
            .then(updateProductStatusIfComplete(document.getProductId(), traceId))
            .thenReturn(toFileUploadResult(result));
    }

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

        int retries = error instanceof CommunicationException ce ? ce.getRetryCount() : 0;
        return saveErrorLog(document.getDocumentId(), document.getFilename(), traceId, errorCode, retries)
            .then(documentRepository.updateStatus(document.getDocumentId(), status, traceId, null, errorCode))
            .then(updateProductStatusIfComplete(document.getProductId(), traceId))
            .thenReturn(buildResult(status, error.getMessage(), null, traceId, document.getDocumentId(), false));
    }

    protected boolean isRetryableError(Throwable error) {
        if (error instanceof CommunicationException ce) {
            String code = ce.getErrorCode();
            return DocumentErrorCodes.TIMEOUT.equals(code) || DocumentErrorCodes.GATEWAY_TIMEOUT.equals(code);
        }
        String message = error.getMessage();
        if (message == null) return false;
        return message.contains(DocumentErrorCodes.MSG_TIMEOUT) || message.contains(DocumentErrorCodes.MSG_TIMEOUT_TITLE) || message.contains(DocumentErrorCodes.TIMEOUT);
    }

    protected String extractErrorCode(Throwable error) {
        if (error instanceof CommunicationException ce) {
            return ce.getErrorCode();
        }
        if (error.getCause() instanceof CommunicationException ce) {
            return ce.getErrorCode();
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

    private FileUploadResult buildSkippedResult(String externalReference, String message, String status) {
        return FileUploadResult.builder()
            .status(status)
            .message(message)
            .traceId(UUID.randomUUID().toString())
            .processedAt(Instant.now())
            .externalReference(externalReference)
            .success(true)
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