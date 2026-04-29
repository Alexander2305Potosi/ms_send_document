package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ZipArchive;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
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
    protected final ProductRestGateway productRestGateway;

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
            .flatMap(claimed -> downloadContentIfNeeded(pending, traceId))
            .flatMap(docWithContent -> {
                if (docWithContent.isZipArchive()) {
                    return processZipDocument(docWithContent, traceId);
                } else {
                    return processDocumentInternal(docWithContent, traceId);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("Document {} claimed by another instance", pending.getDocumentId());
                return Mono.empty();
            }));
    }

    /**
     * Downloads document content on-demand from REST API if not already present.
     */
    private Mono<ProductDocumentToProcess> downloadContentIfNeeded(ProductDocumentToProcess pending, String traceId) {
        if (pending.getContent() != null && pending.getContent().length > 0) {
            log.debug("Document {} already has content, skipping download", pending.getDocumentId());
            return Mono.just(pending);
        }

        log.info("Downloading content for document {} from REST API, traceId: {}",
            pending.getDocumentId(), traceId);

        return productRestGateway.getDocument(pending.getProductId(), pending.getDocumentId(), traceId)
            .flatMap(docInfo -> {
                byte[] content = docInfo.content() != null ? docInfo.content() : new byte[0];
                boolean isZip = isZipArchive(docInfo, pending);
                return documentRepository.updateContent(pending.getDocumentId(), content)
                    .thenReturn(ProductDocumentToProcess.builder()
                        .documentId(pending.getDocumentId())
                        .productId(pending.getProductId())
                        .parentDocumentId(pending.getParentDocumentId())
                        .filename(docInfo.filename() != null ? docInfo.filename() : pending.getFilename())
                        .content(content)
                        .contentType(docInfo.contentType() != null ? docInfo.contentType() : pending.getContentType())
                        .origin(pending.getOrigin())
                        .status(pending.getStatus())
                        .createdAt(pending.getCreatedAt())
                        .isZipArchive(isZip)
                        .build());
            })
            .doOnSuccess(doc -> log.info("Content downloaded for document {}, size: {} bytes, isZip: {}",
                pending.getDocumentId(),
                doc.getContent() != null ? doc.getContent().length : 0,
                doc.isZipArchive()))
            .doOnError(error -> log.error("Failed to download document {}: {}",
                pending.getDocumentId(), error.getMessage()));
    }

    private boolean isZipArchive(ProductDocumentInfo docInfo, ProductDocumentToProcess pending) {
        if (docInfo != null && docInfo.isZip()) {
            return true;
        }
        String filename = docInfo != null ? docInfo.filename() : pending.getFilename();
        return filename != null && filename.toLowerCase().endsWith(".zip");
    }

    /**
     * Processes a ZIP document by extracting its children and processing each.
     */
    private Mono<FileUploadResult> processZipDocument(ProductDocumentToProcess zipDoc, String traceId) {
        return extractZipChildren(zipDoc, traceId)
            .flatMap(children -> children.isEmpty()
                ? handleEmptyZip(zipDoc, traceId)
                : processZipChildren(zipDoc, children, traceId));
    }

    private Mono<List<ZipArchive.ExtractedDocument>> extractZipChildren(ProductDocumentToProcess zipDoc, String traceId) {
        try {
            ZipArchive archive = ZipArchive.builder()
                .zipContent(zipDoc.getContent())
                .originalFilename(zipDoc.getFilename())
                .build();
            List<ZipArchive.ExtractedDocument> children = archive.extractDocuments();
            log.info("ZIP {} contains {} documents", zipDoc.getFilename(), children.size());
            return Mono.just(children);
        } catch (Exception e) {
            log.error("Failed to extract ZIP {}: {}", zipDoc.getFilename(), e.getMessage());
            documentRepository.updateStatus(
                    zipDoc.getDocumentId(), DocumentStatus.FAILURE.name(), traceId,
                    null, ProcessingResultCodes.ZIP_EXTRACTION_FAILED)
                .subscribe();
            return Mono.just(List.of());
        }
    }

    private Mono<FileUploadResult> handleEmptyZip(ProductDocumentToProcess zipDoc, String traceId) {
        log.warn("ZIP {} is empty", zipDoc.getFilename());
        return documentRepository.updateStatus(
                zipDoc.getDocumentId(), DocumentStatus.SUCCESS.name(), traceId, null, null)
            .thenReturn(successResult(zipDoc.getDocumentId(), traceId, "ZIP was empty"));
    }

    private Mono<FileUploadResult> processZipChildren(
            ProductDocumentToProcess zipDoc,
            List<ZipArchive.ExtractedDocument> children,
            String traceId) {
        return Flux.fromIterable(children)
            .flatMapSequential(extracted -> processZipChild(zipDoc, extracted, traceId))
            .collectList()
            .flatMap(results -> aggregateZipResults(zipDoc, results, traceId));
    }

    private Mono<FileUploadResult> processZipChild(
            ProductDocumentToProcess zipDoc,
            ZipArchive.ExtractedDocument extracted,
            String traceId) {
        ProductDocumentToProcess childDoc = buildChildDocument(zipDoc, extracted);
        return documentRepository.save(childDoc)
            .then(processDocumentInternal(childDoc, traceId));
    }

    private ProductDocumentToProcess buildChildDocument(
            ProductDocumentToProcess zipDoc,
            ZipArchive.ExtractedDocument extracted) {
        return ProductDocumentToProcess.builder()
            .documentId(zipDoc.getDocumentId() + "_" + extracted.getFilename())
            .productId(zipDoc.getProductId())
            .parentDocumentId(zipDoc.getDocumentId())
            .filename(extracted.getFilename())
            .content(extracted.getContent())
            .contentType(extracted.getContentType())
            .origin(zipDoc.getOrigin())
            .status(DocumentStatus.PENDING.name())
            .createdAt(Instant.now())
            .isZipArchive(false)
            .build();
    }

    private Mono<FileUploadResult> aggregateZipResults(
            ProductDocumentToProcess zipDoc,
            List<FileUploadResult> results,
            String traceId) {
        boolean allSuccess = results.stream().allMatch(FileUploadResult::isSuccess);
        String status = allSuccess ? DocumentStatus.SUCCESS.name() : DocumentStatus.FAILURE.name();
        String errorCode = allSuccess ? null : ProcessingResultCodes.ZIP_PARTIAL_FAILURE;

        return documentRepository.updateStatus(
                zipDoc.getDocumentId(), status, traceId, null, errorCode)
            .thenReturn(FileUploadResult.builder()
                .status(status)
                .correlationId(zipDoc.getDocumentId())
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(allSuccess)
                .errorCode(errorCode)
                .message(summaryMessage(allSuccess, results.size()))
                .build());
    }

    private FileUploadResult successResult(String correlationId, String traceId, String message) {
        return FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId(correlationId)
            .traceId(traceId)
            .processedAt(Instant.now())
            .success(true)
            .message(message)
            .build();
    }

    private String summaryMessage(boolean allSuccess, int count) {
        return allSuccess
            ? "ZIP processed successfully with " + count + " documents"
            : "ZIP processed with failures: " + count + " documents";
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
