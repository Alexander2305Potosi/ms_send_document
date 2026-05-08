package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractDocumentProcessingUseCase {

    private final Logger log = Logger.getLogger(getClass().getName());

    private final DocumentRepository documentRepository;
    private final DocumentHistoryRepository historyRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    protected AbstractDocumentProcessingUseCase(
            DocumentRepository documentRepository,
            DocumentHistoryRepository historyRepository,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator) {
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.productRestGateway = productRestGateway;
        this.documentValidator = documentValidator;
    }

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
                traceFailure(doc.id(), doc.name(), implementationName(), error);
                documentRepository.updateStateById(doc.id(), ProductState.PENDING, LocalDateTime.now()).subscribe();
                log.log(Level.SEVERE, "Document {0} failed unexpectedly, re-queued for next execution: {1}",
                    new Object[]{doc.documentId(), error.getMessage()});
                return Flux.empty();
            });
    }

    private Flux<FileUploadResult> processDocument(Document doc) {
        Long docId = doc.id();
        return productRestGateway.getDocument(doc.productId(), doc.documentId())
            .map(this::toProductDocument)
            .flatMapMany(file -> decompressIfNeeded(file))
            .flatMap(validated -> documentValidator.validate(validated, true)
                .onErrorResume(ProcessingException.class,
                    error -> skipDocByBussines(doc, error, validated.filename()))
                .flatMap(v -> uploadDocument(v, doc.productId(), docId)
                    .flatMap(result -> handleSuccess(doc, result, v.filename()))
                    .onErrorResume(error -> handleError(doc, error, v.filename()))
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

    private Mono<ProductDocumentHistory> skipDocByBussines(Document doc, ProcessingException error, String filename) {
        log.log(Level.INFO, "Document {0} skipped: {1}", new Object[]{doc.documentId(), error.getMessage()});
        traceSkip(doc, filename, error);
        documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
        return Mono.empty();
    }

    private Mono<FileUploadResult> handleSuccess(Document doc, FileUploadResult result, String filename) {
        traceSuccess(doc.id(), filename, implementationName());
        documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
        return Mono.just(result);
    }

    private Mono<FileUploadResult> handleError(Document doc, Throwable error, String filename) {
        traceFailure(doc.id(), filename, implementationName(), error);
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

    // ── Fire-and-forget trace ─────────────────────────────────────────

    private void saveTrace(DocumentHistory history) {
        historyRepository.save(history)
            .doOnError(e -> log.log(Level.WARNING, "Failed to record trace: {0}", e.getMessage()))
            .subscribe();
    }

    private void traceSkip(Document doc, String filename, ProcessingException error) {
        saveTrace(DocumentHistory.builder()
            .documentId(doc.id())
            .filename(filename)
            .operation(implementationName())
            .result(DocumentStatus.FAILURE.name())
            .errorCode(error.getErrorCode())
            .errorMessage(error.getMessage())
            .retry(0)
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build());
    }

    private void traceSuccess(Long docId, String filename, String operation) {
        saveTrace(DocumentHistory.builder()
            .documentId(docId)
            .filename(filename)
            .operation(operation)
            .result(DocumentStatus.SUCCESS.name())
            .retry(0)
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build());
    }

    private void traceFailure(Long docId, String filename, String operation, Throwable error) {
        historyRepository.findLastAudit(docId, operation)
            .defaultIfEmpty(DocumentHistory.builder().retry(0).build())
            .map(last -> last.retry() != null ? last.retry() + 1 : 1)
            .flatMap(retry -> {
                String errorCode = error instanceof ProcessingException pe
                    ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
                return historyRepository.save(DocumentHistory.builder()
                    .documentId(docId)
                    .filename(filename)
                    .operation(operation)
                    .result(DocumentStatus.FAILURE.name())
                    .errorCode(errorCode)
                    .errorMessage(error.getMessage())
                    .stackTrace(buildStackTrace(error))
                    .retry(retry)
                    .startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build());
            })
            .doOnError(e -> log.log(Level.WARNING, "Failed to record failure trace: {0}", e.getMessage()))
            .subscribe();
    }

    private static String buildStackTrace(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append(element.toString()).append('\n');
        }
        return sb.toString();
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
