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
            .flatMap(doc -> {
                String docId = doc.getDocumentId();
                return retrieveDocumentContent(doc)
                    .doOnError(e -> log.error("Pipeline error at stage=RETRIEVE for doc=[{}]: {}", docId, e.getMessage()));
            })
            .flatMap(doc -> {
                String docId = doc.getDocumentId();
                return validateDocument(doc)
                    .doOnError(e -> log.error("Pipeline error at stage=VALIDATE for doc=[{}]: {}", docId, e.getMessage()));
            })
            .flatMap(doc -> {
                String docId = doc.getDocumentId();
                return uploadDocument(doc)
                    .doOnError(e -> log.error("Pipeline error at stage=UPLOAD for doc=[{}]: {}", docId, e.getMessage()));
            })
            .doOnTerminate(() -> log.info("Pipeline {} completed", implementationName()))
            .doOnNext(r -> log.info("Document processed: correlationId={}, status={}",
                r.getCorrelationId(), r.getStatus()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()))
            .doOnCancel(() -> log.warn("Pipeline {} cancelled", implementationName()));
    }

    protected final Mono<ProductDocumentToProcess> retrieveDocumentContent(ProductDocumentToProcess doc) {
        if (doc.getContent() != null && doc.getContent().length > 0) {
            log.debug("Document {} already has content", doc.getDocumentId());
            return Mono.just(doc);
        }

        log.info("Downloading content for document {} from REST API", doc.getDocumentId());

        return productRestGateway.getDocument(doc.getProductId(), doc.getDocumentId())
            .flatMap(docInfo -> {
                byte[] content = docInfo.content() != null ? docInfo.content() : new byte[0];
                boolean isZip = docInfo.isZip() ||
                    (docInfo.filename() != null && docInfo.filename().toLowerCase().endsWith(".zip"));
                double fileSizeMb = content.length / (1024.0 * 1024.0);

                ProductDocumentToProcess updated = doc.withContent(content, docInfo.filename(), docInfo.contentType(), fileSizeMb);
                return documentRepository.updateContent(doc.getDocumentId(), content)
                    .thenReturn(updated);
            });
    }

    protected final Mono<ProductDocumentToProcess> validateDocument(ProductDocumentToProcess pending) {
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> applyRules(pending));
    }

    protected abstract Mono<ProductDocumentToProcess> applyRules(ProductDocumentToProcess pending);

    protected abstract Mono<FileUploadResult> uploadDocument(ProductDocumentToProcess doc);

    protected abstract String implementationName();
}
