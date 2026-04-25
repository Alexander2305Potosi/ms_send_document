package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProcessProductDocumentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessProductDocumentsUseCase.class);

    private static final String DEFAULT_ERROR_CODE = "UNKNOWN_ERROR";
    private static final int DEFAULT_RETRY_COUNT = 0;
    private static final long MB_TO_BYTES = 1024 * 1024;

    private final ProductDocumentRepository documentRepository;
    private final ExternalSoapGateway soapGateway;
    private final FileValidator fileValidator;
    private final SoapCommunicationLogRepository logRepository;
    private final FileValidationConfig validationConfig;

    public ProcessProductDocumentsUseCase(ProductDocumentRepository documentRepository,
                                        ExternalSoapGateway soapGateway,
                                        FileValidator fileValidator,
                                        SoapCommunicationLogRepository logRepository,
                                        FileValidationConfig validationConfig) {
        this.documentRepository = documentRepository;
        this.soapGateway = soapGateway;
        this.fileValidator = fileValidator;
        this.logRepository = logRepository;
        this.validationConfig = validationConfig;
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

        if (shouldSkipFolder(pending.getOrigin())) {
            log.info("Document {} skipped due to folder rule, origin: {}",
                pending.getDocumentId(), pending.getOrigin());
            return documentRepository.updateStatus(
                    pending.getDocumentId(),
                    DocumentStatus.SKIPPED_VALUE,
                    traceId,
                    null,
                    "SKIPPED_FOLDER")
                .thenReturn(FileUploadResult.builder()
                    .status(DocumentStatus.SKIPPED_VALUE)
                    .message("Document skipped due to folder rule: " + pending.getOrigin())
                    .correlationId(null)
                    .traceId(traceId)
                    .processedAt(Instant.now())
                    .externalReference(pending.getDocumentId())
                    .success(true)
                    .build());
        }

        if (!shouldSendByOrigin(pending.getOrigin())) {
            log.info("Document {} NOT SENT due to origin pattern rule, origin: {}",
                pending.getDocumentId(), pending.getOrigin());
            return documentRepository.updateStatus(
                    pending.getDocumentId(),
                    DocumentStatus.NOT_SENT_VALUE,
                    traceId,
                    null,
                    "NOT_SENT_ORIGIN")
                .thenReturn(FileUploadResult.builder()
                    .status(DocumentStatus.NOT_SENT_VALUE)
                    .message("Document not sent: origin does not match required patterns: " + pending.getOrigin())
                    .correlationId(null)
                    .traceId(traceId)
                    .processedAt(Instant.now())
                    .externalReference(pending.getDocumentId())
                    .success(true)
                    .build());
        }

        return processFile(pending, traceId)
            .flatMap(result -> updateDocumentStatus(pending.getDocumentId(), result, traceId).thenReturn(result))
            .onErrorResume(error -> handleDocumentError(pending.getDocumentId(), error, traceId));
    }

    private boolean shouldSendByOrigin(String origin) {
        List<String> patterns = validationConfig.originPatternsToSend();
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> origin.contains(pattern));
    }

    private boolean shouldSkipFolder(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        List<String> foldersToSkip = validationConfig.foldersToSkip();
        if (foldersToSkip == null || foldersToSkip.isEmpty()) {
            return false;
        }
        return foldersToSkip.stream()
            .anyMatch(folder -> origin.contains(folder));
    }

    private Mono<FileUploadResult> updateDocumentStatus(String documentId, FileUploadResult result, String traceId) {
        String status = result.getStatus();
        String soapCorrelationId = DocumentStatus.SUCCESS_VALUE.equals(status) ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS_VALUE.equals(status) ? null : extractErrorCodeFromMessage(result.getMessage());

        return documentRepository.updateStatus(documentId, status, traceId, soapCorrelationId, errorCode)
            .thenReturn(result);
    }

    private Mono<FileUploadResult> handleDocumentError(String documentId, Throwable error, String traceId) {
        String errorCode = extractErrorCode(error);
        String status = isRetryableError(error) ? DocumentStatus.RETRY_VALUE : DocumentStatus.FAILURE_VALUE;
        log.error("Failed to process document {}: {} (status={}, errorCode={})",
            documentId, error.getMessage(), status, errorCode);

        return documentRepository.updateStatus(documentId, status, traceId, null, errorCode)
            .then(Mono.error(error));
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            String code = sce.getErrorCode();
            return "TIMEOUT".equals(code) || "GATEWAY_TIMEOUT".equals(code);
        }
        String message = error.getMessage();
        if (message == null) return false;
        return message.contains("timeout") || message.contains("Timeout") || message.contains("TIMEOUT");
    }

    private FileUploadResult toResult(SoapResponse response) {
        return FileUploadResult.builder()
            .status(response.getStatus())
            .message(response.getMessage())
            .correlationId(response.getCorrelationId())
            .traceId(response.getTraceId())
            .processedAt(response.getProcessedAt())
            .externalReference(response.getExternalReference())
            .success(response.isSuccess())
            .build();
    }

    private Mono<FileUploadResult> processFile(ProductDocumentToProcess document, String traceId) {
        FileData fileData = FileData.builder()
            .content(document.getContent())
            .filename(document.getFilename())
            .size(document.getContent() != null ? document.getContent().length : 0)
            .contentType(document.getContentType())
            .traceId(traceId)
            .build();

        // Check if file should NOT be sent (< 50MB rule)
        if (shouldNotSendBySize(fileData.getSize())) {
            log.info("Document {} NOT SENT due to size rule: {} bytes (<= {} MB). Trace: {}",
                fileData.getFilename(), fileData.getSize(), validationConfig.maxFileSizeMb(), traceId);
            return Mono.just(FileUploadResult.builder()
                .status(DocumentStatus.NOT_SENT_VALUE)
                .message("Document not sent: file size " + fileData.getSize() + " bytes exceeds limit (>= " + validationConfig.maxFileSizeMb() + " MB)")
                .correlationId(null)
                .traceId(traceId)
                .processedAt(Instant.now())
                .externalReference(fileData.getFilename())
                .success(true)
                .build());
        }

        FolderInfo folderInfo = extractFolderInfo(document.getOrigin());

        return fileValidator.validate(fileData)
            .map(validData -> SoapRequest.fromFileData(validData, folderInfo.parentFolder(), folderInfo.childFolder()))
            .flatMap(soapGateway::sendFile)
            .flatMap(response -> saveSoapLog(fileData.getFilename(), fileData.getTraceId(), response).thenReturn(response))
            .map(this::toResult)
            .doOnNext(result -> log.info("File {} processed, correlationId: {}", fileData.getFilename(), result.getCorrelationId()))
            .onErrorResume(error -> {
                // Validation errors (invalid type, etc.) -> NOT_SENT with reason
                if (error instanceof com.example.fileprocessor.domain.exception.FileValidationException fve) {
                    log.info("Document {} NOT SENT due to validation rule: {}. Trace: {}",
                        fileData.getFilename(), fve.getMessage(), traceId);
                    return saveSoapErrorLog(fileData.getFilename(), traceId, error)
                        .thenReturn(FileUploadResult.builder()
                            .status(DocumentStatus.NOT_SENT_VALUE)
                            .message("Document not sent: " + fve.getMessage())
                            .correlationId(null)
                            .traceId(traceId)
                            .processedAt(Instant.now())
                            .externalReference(fileData.getFilename())
                            .success(true)
                            .build());
                }
                // SOAP errors -> FAILURE
                return saveSoapErrorLog(fileData.getFilename(), fileData.getTraceId(), error)
                    .then(Mono.error(error));
            });
    }

    private boolean shouldNotSendBySize(long sizeBytes) {
        int maxSizeMb = validationConfig.maxFileSizeMb();
        if (maxSizeMb <= 0) {
            return false;
        }
        // NOT_SENT if size >= 50MB (only files < 50MB are sent)
        return sizeBytes >= (long) maxSizeMb * MB_TO_BYTES;
    }

    private FolderInfo extractFolderInfo(String origin) {
        List<String> keywords = validationConfig.keywords();
        if (keywords == null || keywords.isEmpty() || origin == null || origin.isBlank()) {
            return new FolderInfo(".", ".");
        }

        for (String keyword : keywords) {
            if (origin.contains(keyword)) {
                String[] parts = origin.split("/");
                if (parts.length >= 2) {
                    String childFolder = parts[parts.length - 1];
                    String parentFolder = parts.length > 1 ? parts[parts.length - 2] : ".";
                    return new FolderInfo(parentFolder, childFolder);
                }
                return new FolderInfo(origin, ".");
            }
        }
        return new FolderInfo(".", ".");
    }

    private record FolderInfo(String parentFolder, String childFolder) {}

    private Mono<Void> saveSoapLog(String filename, String traceId, SoapResponse response) {
        SoapCommunicationLog dbLog = SoapCommunicationLog.builder()
            .traceId(traceId)
            .status(DocumentStatus.SUCCESS_VALUE)
            .retryCount(DEFAULT_RETRY_COUNT)
            .filename(filename)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }

    private Mono<Void> saveSoapErrorLog(String filename, String traceId, Throwable error) {
        SoapCommunicationException sce = unwrapSoapException(error);
        String errorCode = sce != null ? sce.getErrorCode() : DEFAULT_ERROR_CODE;
        int retries = sce != null ? sce.getRetryCount() : DEFAULT_ERROR_CODE.length();

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

    private String extractErrorCode(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce.getErrorCode();
        }
        return DEFAULT_ERROR_CODE;
    }

    private String extractErrorCodeFromMessage(String message) {
        if (message == null) return DEFAULT_ERROR_CODE;
        if (message.contains("timeout")) return "TIMEOUT";
        if (message.contains("validation")) return "VALIDATION_ERROR";
        return DEFAULT_ERROR_CODE;
    }

    private SoapCommunicationException unwrapSoapException(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce;
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce;
        }
        return null;
    }
}
