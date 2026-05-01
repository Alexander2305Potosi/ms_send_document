package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.DocumentValidationGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Abstract base for document processing use cases.
 * Handles shared orchestration; subclasses implement gateway-specific behavior.
 */
@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ProductRestGateway productRestGateway;
    protected final DocumentValidationGateway documentValidator;

    public Flux<FileUploadResult> executePendingDocuments() {
        return productRestGateway.getAllProducts()
            .concatMap(product -> Flux.fromIterable(product.documents())
                .flatMap(doc -> productRestGateway.getDocument(product.productId(), doc.documentId())
                    .flatMap(documentValidator::validate)
                    .flatMap(validated -> uploadDocument(validated, product.productId()))))
            .doOnTerminate(() -> log.info("Pipeline {} completed", implementationName()))
            .doOnError(e -> log.error("Pipeline error: {}", e.getMessage()))
            .doOnCancel(() -> log.warn("Pipeline {} cancelled", implementationName()));
    }

    protected abstract Mono<FileUploadResult> uploadDocument(ProductDocument doc, String productId);

    protected abstract String implementationName();

    protected FileUploadRequest buildFileUploadRequest(ProductDocument doc, String origin) {
        return FileUploadRequest.builder()
            .documentId(doc.documentId())
            .content(doc.content() != null ? doc.content() : new byte[0])
            .filename(doc.filename())
            .contentType(doc.contentType())
            .fileSize(doc.size())
            .origin(origin)
            .build();
    }

    protected Mono<FileUploadResult> handleUploadError(Throwable error) {
        String errorCode = error instanceof com.example.fileprocessor.domain.exception.ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        log.error("Upload failed with errorCode={}: {}", errorCode, error.getMessage());
        return Mono.just(FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .build());
    }
}
