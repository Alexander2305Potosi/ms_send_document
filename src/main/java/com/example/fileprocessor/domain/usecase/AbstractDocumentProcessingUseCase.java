package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base for document processing use cases.
 * Handles shared orchestration; subclasses implement repository-specific behavior.
 */
@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = Logger.getLogger(getClass().getName());

    protected final ProductRepository productRepository;
    protected final DocumentHistoryRepository historyRepository;
    protected final ProductRestGateway productRestGateway;
    protected final RulesBussinesGateway documentValidator;

    public Flux<FileUploadResult> executePendingDocuments() {
        return productRepository.findByLoadDate(LocalDate.now())
            .concatMap(product -> {
                String productId = product.productId();
                return markProductInProgress(productId)
                    .thenMany(Flux.fromIterable(product.documents())
                        .flatMap(doc -> processDocument(doc, productId))
                        .collectList()
                        .flatMapMany(results -> {
                            markProductFinished(productId, results).subscribe();
                            return Flux.fromIterable(results);
                        }));
            })
            .doOnTerminate(() -> log.log(Level.INFO, "Pipeline {0} completed", new Object[]{implementationName()}))
            .doOnError(e -> log.log(Level.SEVERE, "Pipeline error: {0}", new Object[]{e.getMessage()}))
            .doOnCancel(() -> log.log(Level.WARNING, "Pipeline {0} cancelled", new Object[]{implementationName()}));
    }

    private Mono<Void> markProductInProgress(String productId) {
        return productRepository.updateEstado(productId, ProductState.IN_PROGRESS);
    }

    private Mono<Void> markProductFinished(String productId, List<FileUploadResult> results) {
        boolean anyFailure = results.stream().anyMatch(r -> !r.isSuccess());
        String finalState = anyFailure ? ProductState.FAILED : ProductState.PROCESSED;
        return productRepository.updateEstado(productId, finalState);
    }

    private Mono<FileUploadResult> processDocument(ProductDocument doc, String productId) {
        Flux<ProductDocument> documentFlux = productRestGateway.getDocument(productId, doc.documentId())
            .flatMapMany(this::decompressIfNeeded)
            .flatMap(documentValidator::validate)
            .switchIfEmpty(Mono.defer(() -> {
                ProcessingException validationError = new ProcessingException(
                    "Document validation failed", ProcessingResultCodes.INVALID_RESPONSE, doc.documentId());
                return Mono.error(validationError);
            }));

        return documentFlux
            .flatMap(validated -> uploadDocument(validated, productId)
                .flatMap(result -> saveHistory(validated, productId, result)
                    .thenReturn(result)))
            .onErrorResume(error -> saveFailedHistory(doc, productId, error))
            .single();
    }

    private Mono<FileUploadResult> saveFailedHistory(ProductDocument doc, String productId, Throwable error) {
        String errorCode = error instanceof ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;

        FileUploadResult result = FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .build();

        return saveHistory(doc, productId, result)
            .thenReturn(result);
    }

    private Mono<Void> saveHistory(ProductDocument doc, String productId, FileUploadResult result) {
        boolean isSuccess = result.isSuccess();
        DocumentHistory record = new DocumentHistory(
            null,
            productId,
            doc.documentId(),
            doc.filename(),
            doc.isZip() ? doc.filename() : null,
            isSuccess ? DocumentStatus.SUCCESS.name() : DocumentStatus.FAILURE.name(),
            result.getErrorCode(),
            result.getMessage(),
            result.getAttemptCount(),
            isSuccess ? LocalDateTime.now() : null,
            !isSuccess ? LocalDateTime.now() : null,
            LocalDateTime.now()
        );
        return historyRepository.save(record);
    }

    private Flux<ProductDocument> decompressIfNeeded(ProductDocument doc) {
        return ZipDecompressor.decompress(doc);
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
        log.log(Level.SEVERE, "Upload failed with errorCode={0}: {1}", new Object[]{errorCode, error.getMessage()});
        return Mono.just(FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .build());
    }
}