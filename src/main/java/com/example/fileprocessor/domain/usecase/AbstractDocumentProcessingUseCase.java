package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base use case for document processing.
 * Refactored to remove old state management and tracing.
 */
public abstract class AbstractDocumentProcessingUseCase {

    private final Logger log = Logger.getLogger(getClass().getName());

    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    protected AbstractDocumentProcessingUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator) {
        this.documentRepository = documentRepository;
        this.productRestGateway = productRestGateway;
        this.documentValidator = documentValidator;
    }

    public Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findByStateAndUseCase(ProductState.PENDING, implementationName())
            .concatMap(this::processDocument) // Direct processing, state locking removed for refactor
            .doOnTerminate(() -> log.log(Level.INFO, "Pipeline {0} completed", new Object[]{implementationName()}));
    }

    private Flux<FileUploadResult> processDocument(Document doc) {
        Long docId = doc.id();
        return productRestGateway.getDocument(doc.productId(), doc.documentId())
            .map(this::toProductDocument)
            .flatMapMany(this::decompressIfNeeded)
            .flatMap(validated -> documentValidator.validate(validated, true)
                .onErrorResume(ProcessingException.class, error -> {
                    log.log(Level.INFO, "Document {0} skipped: {1}", new Object[]{doc.documentId(), error.getMessage()});
                    return Mono.empty();
                })
                .flatMap(v -> uploadDocument(v, doc.productId(), docId)
                    .onErrorResume(this::handleUploadError)
                )
            );
    }

    private Flux<ProductDocumentHistory> decompressIfNeeded(ProductDocumentHistory file) {
        if (!file.isZip() || file.filename() == null || file.filename().isBlank()) {
            return Flux.just(file);
        }
        return ZipDecompressor.decompress(file)
            .onErrorMap(error -> new ProcessingException(
                ProcessingResultCodes.DECOMPRESSION_ERROR,
                "Failed to decompress ZIP '" + file.filename() + "': " + error.getMessage(),
                error));
    }

    // ── Shared helpers ────────────────────────────────────────────────

    protected ProductDocumentHistory toProductDocument(ProductDocumentFile file) {
        return ProductDocumentHistory.builder()
            .documentId(file.documentId())
            .filename(file.filename())
            .content(file.content())
            .contentType(file.contentType())
            .size(file.size())
            .isZip(file.isZip())
            .origin(file.origin())
            .pais(file.pais())
            .build();
    }

    protected FileUploadRequest buildFileUploadRequest(ProductDocumentHistory doc, String origin, Long docId) {
        return FileUploadRequest.builder()
            .documentId(doc.documentId())
            .content(doc.content() != null ? doc.content() : new byte[0])
            .filename(doc.filename())
            .contentType(doc.contentType())
            .fileSize(doc.size())
            .origin(origin)
            .docId(docId)
            .build();
    }

    protected Mono<FileUploadResult> handleUploadError(Throwable error) {
        String errorCode = error instanceof ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        log.log(Level.SEVERE, "Upload failed with errorCode={0}: {1}",
            new Object[]{errorCode, error.getMessage()});
        return Mono.just(FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    protected abstract Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId, Long docId);

    protected abstract String implementationName();
}
