package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstract base for document processing use cases.
 * Handles shared orchestration; subclasses implement gateway-specific behavior.
 */
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ProductDocumentRepository documentRepository;
    protected final ProductRestGateway productRestGateway;

    protected AbstractDocumentProcessingUseCase(
            ProductDocumentRepository documentRepository,
            ProductRestGateway productRestGateway) {
        this.documentRepository = documentRepository;
        this.productRestGateway = productRestGateway;
    }

    public final Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .limitRate(10)
            .flatMap(doc -> {
                String docId = doc.getDocumentId();
                return validateMetadataDocument(doc)
                    .doOnError(e -> log.error("Pipeline error at stage=VALIDATE for doc=[{}]: {}", docId, e.getMessage()));
            })
            .flatMap(doc -> {
                String docId = doc.document().getDocumentId();
                return retrieveDocument(doc)
                    .doOnError(e -> log.error("Pipeline error at stage=RETRIEVE for doc=[{}]: {}", docId, e.getMessage()));
            })
            .flatMap(doc -> {
                String docId = doc.document().getDocumentId();
                return uploadDocument(doc)
                    .doOnError(e -> log.error("Pipeline error at stage=UPLOAD for doc=[{}]: {}", docId, e.getMessage()));
            })
            .doOnTerminate(() -> log.info("Pipeline {} completed", implementationName()))
            .doOnNext(r -> log.info("Document processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()))
            .doOnCancel(() -> log.warn("Pipeline {} cancelled", implementationName()));
    }

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
                double fileSizeMb = content.length / (1024.0 * 1024.0);

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
                    .fileSizeMb(fileSizeMb)
                    .build();

                long fileSizeBytes = (long) (fileSizeMb * 1024 * 1024);
                return documentRepository.updateContent(pending.getDocumentId(), content)
                    .thenReturn(new DocumentToUpload(updated, docToUpload.folderInfo(), fileSizeBytes, docToUpload.skipped()));
            });
    }

    protected final Mono<DocumentToUpload> validateMetadataDocument(ProductDocumentToProcess pending) {
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> applyRulesMetadata(pending));
    }

    protected abstract Mono<DocumentToUpload> applyRulesMetadata(ProductDocumentToProcess pending);

    protected abstract Mono<FileUploadResult> uploadDocument(DocumentToUpload validated);

    protected abstract String implementationName();
}
