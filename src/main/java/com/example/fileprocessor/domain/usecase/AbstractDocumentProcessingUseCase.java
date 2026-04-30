package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Abstract base class for document processing use cases.
 * Handles shared orchestration logic; subclasses implement gateway-specific behavior.
 */
@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ProductDocumentRepository documentRepository;
    protected final ProductRestGateway productRestGateway;
    protected final ZipProcessor zipProcessor;

    // ============ TEMPLATE METHOD (final) ============

    public final Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .flatMap(this::validateMetadataDocument)
            .flatMap(this::retrieveDocument)
            .flatMap(this::uploadDocument)
            .doOnTerminate(() -> log.info("Pipeline {} completed", implementationName()))
            .doOnNext(r -> log.info("Document processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()));
    }

    // ============ SHARED RETRIEVE LOGIC (final) ============

    /**
     * Downloads document content from REST API if not already present.
     * This logic is shared across all processor implementations.
     */
    protected final Mono<DocumentToUpload> retrieveDocument(DocumentToUpload docToUpload) {
        ProductDocumentToProcess pending = docToUpload.document();

        if (pending.getContent() != null && pending.getContent().length > 0) {
            log.debug("Document {} already has content", pending.getDocumentId());
            return Mono.just(docToUpload);
        }

        log.info("Downloading content for document {} from REST API", pending.getDocumentId());

        return productRestGateway.getDocument(pending.getProductId(), pending.getDocumentId())
            .flatMap(docInfo -> {
                byte[] content = docInfo.content() != null ? docInfo.content() : new byte[0];
                boolean isZip = docInfo.isZip() ||
                    (docInfo.filename() != null && docInfo.filename().toLowerCase().endsWith(".zip"));

                ProductDocumentToProcess updated = ProductDocumentToProcess.builder()
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
                    .build();

                long fileSize = content != null ? content.length : 0;
                return documentRepository.updateContent(pending.getDocumentId(), content)
                    .thenReturn(new DocumentToUpload(updated, docToUpload.folderInfo(), fileSize, docToUpload.skipped()));
            });
    }

    // ============ SHARED ZIP PROCESSING ============

    /**
     * Processes a ZIP document end-to-end: extract, validate, process children, aggregate results.
     * Uses subclasses' validateMetadataDocument and uploadDocument callbacks.
     */
    protected Mono<DocumentToUpload> processZipDocument(ProductDocumentToProcess zipDoc) {
        return zipProcessor.extractAndValidate(zipDoc)
            .flatMap(extraction -> {
                if (!extraction.hasChildren()) {
                    return documentRepository.updateStatus(
                        zipDoc.getDocumentId(), DocumentStatus.SUCCESS.name(), null, null)
                        .thenReturn(new DocumentToUpload(zipDoc, null, 0, false));
                }
                List<ProductDocumentToProcess> children = extraction.children();
                return documentRepository.saveAll(Flux.fromIterable(children))
                    .then(zipProcessor.processZipChildren(zipDoc, children,
                        this::validateMetadataDocument, this::uploadDocument))
                    .flatMap(result -> {
                        String status = result.allSucceeded() ?
                            DocumentStatus.SUCCESS.name() : DocumentStatus.FAILURE.name();
                        return documentRepository.updateStatus(
                            zipDoc.getDocumentId(), status, null, result.errorCode())
                            .thenReturn(new DocumentToUpload(zipDoc, null, 0, !result.allSucceeded()));
                    });
            })
            .onErrorResume(error -> {
                log.error("ZIP processing failed for {}: {}", zipDoc.getFilename(), error.getMessage());
                return documentRepository.updateStatus(
                    zipDoc.getDocumentId(), DocumentStatus.FAILURE.name(),
                    null, ProcessingResultCodes.ZIP_EXTRACTION_FAILED)
                    .thenReturn(new DocumentToUpload(zipDoc, null, 0, true));
            });
    }

    // ============ SHARED VALIDATION LOGIC (final) ============

    /**
     * Claims a document for processing and then performs validation.
     * Returns empty if document is not available or already claimed.
     * Subclasses implement applyRulesMetadata for specific validation logic.
     */
    protected final Mono<DocumentToUpload> validateMetadataDocument(ProductDocumentToProcess pending) {
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> applyRulesMetadata(pending));
    }

    /**
     * Subclass-specific validation logic after document is claimed.
     * For S3: includes folder exclusion check.
     * For SOAP: direct validation.
     */
    protected abstract Mono<DocumentToUpload> applyRulesMetadata(ProductDocumentToProcess pending);

    // ============ ABSTRACT METHODS (subclasses must implement) ============

    /**
     * Uploads document to the external gateway (SOAP or S3).
     * Uses pre-validated metadata from DocumentToUpload.
     */
    protected abstract Mono<FileUploadResult> uploadDocument(DocumentToUpload validated);

    /**
     * Returns the processor implementation name for logging.
     */
    protected abstract String implementationName();
}