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
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    private final Logger log = Logger.getLogger(getClass().getName());

    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    public Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findByStateAndUseCase(ProductState.PENDING, implementationName())
            .concatMap(this::startProcessing)
            .doOnTerminate(() -> log.log(Level.INFO, "Pipeline {0} completed", new Object[]{implementationName()}))
            .doOnError(e -> log.log(Level.SEVERE, "Pipeline error: {0}", e.getMessage()))
            .doOnCancel(() -> log.log(Level.INFO, "Pipeline {0} cancelled", new Object[]{implementationName()}));
    }

    private Flux<FileUploadResult> startProcessing(Document doc) {
        documentRepository.updateStateById(doc.id(), ProductState.IN_PROGRESS, LocalDateTime.now()).subscribe();
        return processDocument(doc)
            .onErrorResume(error -> {
                documentRepository.updateStateById(doc.id(), ProductState.PENDING, LocalDateTime.now()).subscribe();
                log.log(Level.SEVERE, "Document {0} failed unexpectedly, re-queued for next execution: {1}",
                    new Object[]{doc.documentId(), error.getMessage()});
                return Flux.empty();
            });
    }

    private Flux<FileUploadResult> processDocument(Document doc) {
        return productRestGateway.getDocument(doc.productId(), doc.documentId())
            .map(this::toProductDocument)
            .flatMapMany(file -> decompressIfNeeded(file))
            .flatMap(validated -> documentValidator.validate(validated, true)
                .switchIfEmpty(Mono.defer(() -> skipDocByBussines(doc)))
                .flatMap(v -> uploadDocument(v, doc.productId())
                    .flatMap(result -> handleSuccess(doc, result))
                    .onErrorResume(error -> handleError(doc, error))
                )
            );
    }

    private Flux<ProductDocumentHistory> decompressIfNeeded(ProductDocumentHistory file) {
        if (!file.isZip() || file.filename() == null || file.filename().isBlank()) {
            return Flux.just(file);
        }
        return ZipDecompressor.decompress(file);
    }

    private Mono<ProductDocumentHistory> skipDocByBussines(Document doc) {
        log.log(Level.INFO, "Document {0} skipped by size validation", new Object[]{doc.documentId()});
        documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
        return Mono.empty();
    }

    private Mono<FileUploadResult> handleSuccess(Document doc, FileUploadResult result) {
        documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
        return Mono.just(result);
    }

    private Mono<FileUploadResult> handleError(Document doc, Throwable error) {
        String errorCode = error instanceof ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        documentRepository.updateStateById(doc.id(), ProductState.FAILED, LocalDateTime.now()).subscribe();
        log.log(Level.SEVERE, "Processing failed for doc {0}: {1}", new Object[]{doc.id(), errorCode});
        return Mono.just(FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    // ── Shared helpers for subclasses ─────────────────────────────────

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

    protected FileUploadRequest buildFileUploadRequest(ProductDocumentHistory doc, String origin) {
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

    protected abstract Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId);

    protected abstract String implementationName();
}
