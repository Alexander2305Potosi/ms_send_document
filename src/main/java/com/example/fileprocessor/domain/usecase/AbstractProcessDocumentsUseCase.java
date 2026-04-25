package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
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
    protected static final String DEFAULT_ERROR_CODE = "UNKNOWN_ERROR";
    protected static final int DEFAULT_RETRY_COUNT = 0;

    protected final ProductDocumentRepository documentRepository;
    protected final FileValidator fileValidator;
    protected final SoapCommunicationLogRepository logRepository;
    protected final DocumentValidationRules validationRules;

    protected AbstractProcessDocumentsUseCase(
            ProductDocumentRepository documentRepository,
            FileValidator fileValidator,
            SoapCommunicationLogRepository logRepository,
            FileValidationConfig validationConfig) {
        this.documentRepository = documentRepository;
        this.fileValidator = fileValidator;
        this.logRepository = logRepository;
        this.validationRules = new DocumentValidationRules(validationConfig);
    }

    public Flux<FileUploadResult> executePendingDocuments() {
        log.info("Fetching pending product documents from database...");
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument)
            .doOnNext(response -> log.info("Document processed: correlationId={}", response.getCorrelationId()))
            .doOnError(error -> log.error("Error processing document: {}", error.getMessage()));
    }

    private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> processDocumentClaimed(pending))
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Document {} already claimed by another process or not pending, skipping",
                    pending.getDocumentId());
                return Mono.empty();
            }));
    }

    private Mono<FileUploadResult> processDocumentClaimed(ProductDocumentToProcess pending) {
        String traceId = UUID.randomUUID().toString();
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
                    "SKIPPED_FOLDER")
                .thenReturn(buildResult(DocumentStatus.SKIPPED_VALUE,
                    "Document skipped due to folder rule: " + pending.getOrigin(),
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
                    "NOT_SENT_ORIGIN")
                .thenReturn(buildResult(DocumentStatus.NOT_SENT_VALUE,
                    "Document not sent: origin does not match required patterns: " + pending.getOrigin(),
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
                    "SIZE_EXCEEDED")
                .thenReturn(buildResult(DocumentStatus.NOT_SENT_VALUE,
                    "Document not sent: file size " + fileSize + " bytes exceeds limit",
                    null, traceId, pending.getFilename(), true));
        }

        // Build FileData for validation
        FileData fileData = FileData.builder()
            .content(pending.getContent())
            .filename(pending.getFilename())
            .size(fileSize)
            .contentType(pending.getContentType())
            .traceId(traceId)
            .build();

        DocumentValidationRules.FolderInfo folderInfo = validationRules.extractFolderInfo(pending.getOrigin());

        return fileValidator.validate(fileData)
            .flatMap(validData -> {
                SoapRequest request = SoapRequest.fromFileData(validData,
                    folderInfo.parentFolder(), folderInfo.childFolder());
                return sendDocument(request)
                    .flatMap(result -> updateDocumentStatus(pending.getDocumentId(), result, traceId))
                    .doOnNext(result -> log.info("Document {} sent via {}: correlationId={}",
                        pending.getFilename(), getImplementationName(), result.getCorrelationId()));
            })
            .onErrorResume(error -> handleDocumentError(pending.getDocumentId(), error, traceId));
    }

    // ============== Status Management ==============

    protected Mono<FileUploadResult> updateDocumentStatus(String documentId, DocumentResult result, String traceId) {
        String status = result.getStatus();
        String soapCorrelationId = DocumentStatus.SUCCESS_VALUE.equals(status) ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS_VALUE.equals(status) ? null : extractErrorCodeFromMessage(result.getMessage());

        return documentRepository.updateStatus(documentId, status, traceId, soapCorrelationId, errorCode)
            .thenReturn(toFileUploadResult(result));
    }

    protected Mono<Void> saveSuccessLog(String filename, String traceId, DocumentResult result) {
        SoapCommunicationLog dbLog = SoapCommunicationLog.builder()
            .traceId(traceId)
            .status(DocumentStatus.SUCCESS_VALUE)
            .retryCount(DEFAULT_RETRY_COUNT)
            .filename(filename)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }

    protected Mono<Void> saveErrorLog(String filename, String traceId, String errorCode, int retries) {
        SoapCommunicationLog dbLog = SoapCommunicationLog.builder()
            .traceId(traceId)
            .status(DocumentStatus.FAILURE_VALUE)
            .retryCount(retries)
            .errorCode(errorCode)
            .filename(filename)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }

    // ============== Error Handling ==============

    protected Mono<FileUploadResult> handleDocumentError(String documentId, Throwable error, String traceId) {
        String errorCode = extractErrorCode(error);
        String status = isRetryableError(error) ? DocumentStatus.RETRY_VALUE : DocumentStatus.FAILURE_VALUE;
        log.error("Failed to process document {}: {} (status={}, errorCode={})",
            documentId, error.getMessage(), status, errorCode);

        int retries = error instanceof SoapCommunicationException sce ? sce.getRetryCount() : 0;
        return saveErrorLog(documentId, traceId, errorCode, retries)
            .then(documentRepository.updateStatus(documentId, status, traceId, null, errorCode))
            .thenReturn(buildResult(status, error.getMessage(), null, traceId, documentId, false));
    }

    protected boolean isRetryableError(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            String code = sce.getErrorCode();
            return "TIMEOUT".equals(code) || "GATEWAY_TIMEOUT".equals(code);
        }
        String message = error.getMessage();
        if (message == null) return false;
        return message.contains("timeout") || message.contains("Timeout") || message.contains("TIMEOUT");
    }

    protected String extractErrorCode(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        return DEFAULT_ERROR_CODE;
    }

    protected String extractErrorCodeFromMessage(String message) {
        if (message == null) return DEFAULT_ERROR_CODE;
        if (message.contains("timeout")) return "TIMEOUT";
        if (message.contains("validation")) return "VALIDATION_ERROR";
        return DEFAULT_ERROR_CODE;
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
}
