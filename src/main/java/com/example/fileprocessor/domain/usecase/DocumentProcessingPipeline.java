package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.CommunicationException;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Orchestrates document processing through a pipeline of stages.
 * Each stage is a method with a single responsibility, making the flow easy to read.
 *
 * Pipeline stages:
 * 1. validateBusinessRules - folder/origin/size skip logic
 * 2. validate - file validation (size, type, filename)
 * 3. buildRequest - construct DocumentSendRequest
 * 4. sendWithCircuitBreaker - send with resilience
 * 5. updateStatuses - update document and product status
 */
public class DocumentProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingPipeline.class);

    private final ProductDocumentRepository documentRepository;
    private final FileValidator fileValidator;
    private final DocumentValidationRules validationRules;
    private final DocumentSkipHandler skipHandler;
    private final ProductStatusAggregator statusAggregator;
    private final CircuitBreaker circuitBreaker;
    private final DocumentSender documentSender;

    public DocumentProcessingPipeline(
            ProductDocumentRepository documentRepository,
            FileValidator fileValidator,
            DocumentValidationRules validationRules,
            DocumentSkipHandler skipHandler,
            ProductStatusAggregator statusAggregator,
            CircuitBreaker circuitBreaker,
            DocumentSender documentSender) {
        this.documentRepository = documentRepository;
        this.fileValidator = fileValidator;
        this.validationRules = validationRules;
        this.skipHandler = skipHandler;
        this.statusAggregator = statusAggregator;
        this.circuitBreaker = circuitBreaker;
        this.documentSender = documentSender;
    }

    public Mono<FileUploadResult> process(ProductDocumentToProcess pending, String traceId) {
        return Mono.just(pending)
            .flatMap(doc -> validateBusinessRules(doc, traceId))
            .flatMap(doc -> fileValidator.validate(doc))
            .flatMap(validDoc -> buildRequest(validDoc, traceId))
            .flatMap(this::sendWithCircuitBreaker)
            .flatMap(result -> updateStatuses(pending, result, traceId))
            .doOnNext(result -> log.info("Document {} processed: correlationId={}",
                pending.getFilename(), result.getCorrelationId()));
    }

    private Mono<ProductDocumentToProcess> validateBusinessRules(ProductDocumentToProcess pending, String traceId) {
        log.info("Validating business rules for document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        long fileSize = pending.getContent() != null ? pending.getContent().length : 0;

        if (validationRules.shouldSkipFolder(pending.getOrigin())) {
            return skipHandler.skipDocument(pending, traceId, DocumentStatus.SKIPPED_VALUE,
                ProcessingMessages.MSG_SKIPPED_FOLDER + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER, pending.getDocumentId())
                .then(Mono.empty());
        }
        if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
            return skipHandler.skipDocument(pending, traceId, DocumentStatus.NOT_SENT_VALUE,
                ProcessingMessages.MSG_NOT_SENT_ORIGIN + pending.getOrigin(),
                ProcessingResultCodes.NOT_SENT_ORIGIN, pending.getDocumentId())
                .then(Mono.empty());
        }
        if (validationRules.shouldNotSendBySize(fileSize)) {
            return skipHandler.skipDocument(pending, traceId, DocumentStatus.NOT_SENT_VALUE,
                ProcessingMessages.MSG_SIZE_EXCEEDED + fileSize + ProcessingMessages.MSG_SIZE_EXCEEDED_SUFFIX,
                ProcessingResultCodes.SIZE_EXCEEDED, pending.getFilename())
                .then(Mono.empty());
        }

        return Mono.just(pending);
    }

    private Mono<DocumentSendRequest> buildRequest(ProductDocumentToProcess validDoc, String traceId) {
        DocumentValidationRules.FolderInfo folderInfo = validationRules.extractFolderInfo(validDoc.getOrigin());
        DocumentSendRequest request = DocumentSendRequest.builder()
            .documentId(validDoc.getDocumentId())
            .fileContent(validDoc.getContent())
            .filename(validDoc.getFilename())
            .contentType(validDoc.getContentType())
            .fileSize(validDoc.getContent() != null ? validDoc.getContent().length : 0)
            .traceId(traceId)
            .parentFolder(folderInfo.parentFolder())
            .childFolder(folderInfo.childFolder())
            .build();
        return Mono.just(request);
    }

    private Mono<FileUploadResult> sendWithCircuitBreaker(DocumentSendRequest request) {
        return Mono.just(request)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .flatMap(documentSender::send)
            .onErrorResume(CallNotPermittedException.class, e -> {
                log.warn("Circuit breaker OPEN for document {}: {}",
                    request.getDocumentId(), ProcessingMessages.MSG_CIRCUIT_BREAKER_OPEN);
                return Mono.error(new CommunicationException(
                    ProcessingMessages.MSG_CIRCUIT_BREAKER_OPEN,
                    ProcessingResultCodes.CIRCUIT_BREAKER_OPEN,
                    request.getTraceId(), 0));
            });
    }

    private Mono<FileUploadResult> updateStatuses(ProductDocumentToProcess pending,
                                                  FileUploadResult result, String traceId) {
        String status = result.getStatus();
        String correlationId = DocumentStatus.SUCCESS_VALUE.equals(status) ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS_VALUE.equals(status) ? null : extractErrorCodeFromMessage(result.getMessage());

        return documentRepository.updateStatus(pending.getDocumentId(), status, traceId, correlationId, errorCode)
            .then(statusAggregator.updateProductStatus(pending.getProductId(), traceId))
            .thenReturn(result);
    }

    private String extractErrorCodeFromMessage(String message) {
        if (message == null) return ProcessingResultCodes.UNKNOWN_ERROR;
        if (message.contains(ProcessingMessages.MSG_TIMEOUT)) return ProcessingResultCodes.TIMEOUT;
        if (message.contains(ProcessingMessages.MSG_VALIDATION)) return ProcessingResultCodes.VALIDATION_ERROR;
        return ProcessingResultCodes.UNKNOWN_ERROR;
    }
}