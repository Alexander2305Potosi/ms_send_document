package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ResilienceOperator;
import io.micrometer.core.annotation.Timed;
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
    private final ResilienceOperator resilienceOperator;
    private final DocumentSender documentSender;

    public DocumentProcessingPipeline(
            ProductDocumentRepository documentRepository,
            FileValidator fileValidator,
            DocumentValidationRules validationRules,
            DocumentSkipHandler skipHandler,
            ProductStatusAggregator statusAggregator,
            ResilienceOperator resilienceOperator,
            DocumentSender documentSender) {
        this.documentRepository = documentRepository;
        this.fileValidator = fileValidator;
        this.validationRules = validationRules;
        this.skipHandler = skipHandler;
        this.statusAggregator = statusAggregator;
        this.resilienceOperator = resilienceOperator;
        this.documentSender = documentSender;
    }

    @Timed("document.processing")
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
            return skipHandler.skipDocument(pending, traceId, DocumentStatus.SKIPPED.name(),
                ProcessingMessages.MSG_SKIPPED_FOLDER + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER, pending.getDocumentId())
                .then(Mono.empty());
        }
        if (!validationRules.shouldSendByOrigin(pending.getOrigin())) {
            return skipHandler.skipDocument(pending, traceId, DocumentStatus.NOT_SENT.name(),
                ProcessingMessages.MSG_NOT_SENT_ORIGIN + pending.getOrigin(),
                ProcessingResultCodes.NOT_SENT_ORIGIN, pending.getDocumentId())
                .then(Mono.empty());
        }
        if (validationRules.shouldNotSendBySize(fileSize)) {
            return skipHandler.skipDocument(pending, traceId, DocumentStatus.NOT_SENT.name(),
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
        @SuppressWarnings("unchecked")
        Mono<FileUploadResult> result = (Mono<FileUploadResult>) resilienceOperator.decorate(
            documentSender.send(request),
            request.getTraceId()
        );
        return result;
    }

    private Mono<FileUploadResult> updateStatuses(ProductDocumentToProcess pending,
                                                  FileUploadResult result, String traceId) {
        String status = result.getStatus();
        String correlationId = DocumentStatus.SUCCESS.name().equals(status) ? result.getCorrelationId() : null;
        String errorCode = DocumentStatus.SUCCESS.name().equals(status) ? null : extractErrorCode(result);

        return documentRepository.updateStatus(pending.getDocumentId(), status, traceId, correlationId, errorCode)
            .then(statusAggregator.updateProductStatus(pending.getProductId(), traceId))
            .thenReturn(result);
    }

    private String extractErrorCode(FileUploadResult result) {
        return result.getErrorCode() != null ? result.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
    }
}