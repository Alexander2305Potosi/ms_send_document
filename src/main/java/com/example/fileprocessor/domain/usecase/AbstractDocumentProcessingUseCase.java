package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductInfo;
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

    protected final ProductRestGateway productRestGateway;

    protected AbstractDocumentProcessingUseCase(ProductRestGateway productRestGateway) {
        this.productRestGateway = productRestGateway;
    }

    public Flux<FileUploadResult> executePendingDocuments() {
        return productRestGateway.getAllProducts()
            .flatMap(this::processProductDocuments)
            .doOnTerminate(() -> log.info("Pipeline {} completed", implementationName()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()))
            .doOnCancel(() -> log.warn("Pipeline {} cancelled", implementationName()));
    }

    private Flux<FileUploadResult> processProductDocuments(ProductInfo productInfo) {
        if (productInfo.getDocuments() == null || productInfo.getDocuments().isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(productInfo.getDocuments())
            .flatMap(docInfo -> {
                String docId = docInfo.documentId();
                return productRestGateway.getDocument(productInfo.getProductId(), docId)
                    .flatMap(doc -> uploadDocument(doc, productInfo.getProductId())
                        .doOnError(e -> log.error("Pipeline error for doc=[{}]: {}", docId, e.getMessage())));
            });
    }

    protected abstract Mono<FileUploadResult> uploadDocument(ProductDocumentInfo doc, String productId);

    protected abstract String implementationName();
}
