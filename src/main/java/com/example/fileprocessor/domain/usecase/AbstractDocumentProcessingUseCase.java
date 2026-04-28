package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for document processing use cases.
 * Handles shared orchestration logic; subclasses implement gateway-specific behavior.
 */
@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ProductDocumentRepository documentRepository;
    protected final ProductStatusAggregator statusAggregator;
    protected final FileGateway fileGateway;
    protected final FileValidator fileValidator;

    // ============ TEMPLATE METHOD (final) ============

    public final Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument)
            .doOnTerminate(() -> log.info("Pipeline {} completed", implementationName()))
            .doOnNext(r -> log.info("Document processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()));
    }

    /**
     * Internal orchestration that calls abstract gateway-specific methods.
     * This is the template method - do not override.
     */
    protected final Mono<FileUploadResult> processDocumentInternal(
            ProductDocumentToProcess pending, String traceId) {

        Mono<DocumentSendRequest> request = prepareDocument(pending, traceId)
            .flatMap(doc -> buildRequest(doc, traceId));

        return request
            .flatMap(this::sendWithResilience)
            .flatMap(result -> checkpoint(pending, result, traceId))
            .flatMap(result -> postProcess(pending, result, traceId))
            .doOnNext(result -> log.info("Document {} processed: correlationId={}",
                pending.getFilename(), result.getCorrelationId()));
    }

    // ============ ABSTRACT METHOD (gateway-specific) ============

    /**
     * Prepares document by applying gateway-specific filtering and validation.
     * This combines folder exclusion and document validation into one step.
     */
    protected abstract Mono<ProductDocumentToProcess> prepareDocument(
            ProductDocumentToProcess pending, String traceId);

    /**
     * Returns the processor implementation name for logging.
     */
    protected abstract String implementationName();

    // ============ REQUEST BUILDING (shared) ============

    protected Mono<DocumentSendRequest> buildRequest(ProductDocumentToProcess validDoc, String traceId) {
        FileValidator.FolderInfo folderInfo = fileValidator.extractFolderInfo(validDoc.getOrigin());

        return Mono.just(DocumentSendRequest.builder()
            .documentId(validDoc.getDocumentId())
            .fileContent(validDoc.getContent())
            .filename(validDoc.getFilename())
            .contentType(validDoc.getContentType())
            .fileSize(validDoc.getContent() != null ? validDoc.getContent().length : 0)
            .traceId(traceId)
            .parentFolder(folderInfo.parentFolder())
            .childFolder(folderInfo.childFolder())
            .build());
    }

    // === CLAIMING STRATEGY ===

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

    // === RESILIENCE STRATEGY ===

    protected Mono<FileUploadResult> sendWithResilience(DocumentSendRequest request) {
        return fileGateway.send(request)
            .onErrorResume(error -> {
                String errorCode = extractErrorCode(error);
                return Mono.just(buildFailureResult(errorCode, request.getTraceId()));
            });
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

    // === PERSISTENCE STRATEGY ===

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
}
