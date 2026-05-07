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
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = Logger.getLogger(getClass().getName());

    protected final DocumentRepository documentRepository;
    protected final DocumentHistoryRepository historyRepository;
    protected final ProductRestGateway productRestGateway;
    protected final RulesBussinesGateway documentValidator;

    private static final int MAX_RETRIES = 3;

    public Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findByStateAndUseCase(ProductState.PENDING, implementationName())
            .filterWhen(this::canResumeProcessing)
            .concatMap(this::startProcessing)
            .doOnTerminate(() -> log.log(Level.INFO, "Pipeline {0} completed", new Object[]{implementationName()}))
            .doOnError(e -> log.log(Level.SEVERE, "Pipeline error: {0}", new Object[]{e.getMessage()}))
            .doOnCancel(() -> log.log(Level.WARNING, "Pipeline {0} cancelled", new Object[]{implementationName()}));
    }

    private Mono<Boolean> canResumeProcessing(Document doc) {
        // Check if document was already processed successfully.
        // - No audit record → first time, allow processing (return true)
        // - Last result != SUCCESS → was a failure or skip, allow retry (return true)
        // - Last result == SUCCESS → already processed, skip (return false)
        return historyRepository.findLastAudit(doc.id(), doc.useCase())
            .map(lastAudit -> !"SUCCESS".equals(lastAudit.result()))
            .defaultIfEmpty(true);
    }

    private Flux<FileUploadResult> startProcessing(Document doc) {
        Long docId = doc.id();
        documentRepository.updateStateById(docId, ProductState.IN_PROGRESS, LocalDateTime.now()).subscribe();
        return processDocument(doc);
    }

    private Flux<FileUploadResult> processDocument(Document doc) {
        String documentId = doc.documentId();
        Long docId = doc.id();
        return productRestGateway.getDocument(doc.productId(), documentId)
            .map(this::toProductDocument)
            .flatMapMany(file -> {
                String filename = file.filename();
                if (!file.isZip() || filename == null || filename.isBlank()) {
                    return Flux.just(file);
                }
                return ZipDecompressor.decompress(file);
            })
            .flatMap(validated -> documentValidator.validate(validated, true)
                .switchIfEmpty(Mono.defer(() -> {
                    log.log(Level.INFO, "Document {0} skipped by size validation", documentId);
                    documentRepository.updateStateById(docId, ProductState.PROCESSED, LocalDateTime.now()).subscribe();
                    String skipTraceFilename = validated.parentZipName() != null ? validated.filename() : null;
                    DocumentHistory skipTrace = DocumentHistory.builder()
                        .documentId(docId)
                        .filename(skipTraceFilename)
                        .operation(implementationName())
                        .result("FAILURE")
                        .errorCode(ProcessingResultCodes.BUSINESS_RULE_SKIP)
                        .errorMessage("Skipped by business rule: " + validated.filename())
                        .retry(0)
                        .startedAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .build();
                    historyRepository.save(skipTrace).subscribe();
                    return Mono.empty();
                })))
            .flatMap(validated -> {
                    String traceFilename = validated.parentZipName() != null ? validated.filename() : null;
                    return uploadDocument(validated, doc.productId())
                        .flatMap(result -> handleUploadSuccess(doc, traceFilename, result))
                        .onErrorResume(error -> handleUploadError(doc, traceFilename, error));
                });
    }

    private Mono<FileUploadResult> handleUploadSuccess(Document doc, String traceFilename, FileUploadResult result) {
        documentRepository.updateStateById(doc.id(), ProductState.PROCESSED, LocalDateTime.now()).subscribe();
        DocumentHistory trace = DocumentHistory.builder()
            .documentId(doc.id())
            .filename(traceFilename)
            .operation(implementationName())
            .result("SUCCESS")
            .retry(0)
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build();
        historyRepository.save(trace).subscribe();
        return Mono.just(result);
    }

    private Mono<FileUploadResult> handleUploadError(Document doc, String traceFilename, Throwable error) {
        String errorCode = error instanceof ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        String errorMsg = error.getMessage();
        String stackTrace = getStackTrace(error);

        return historyRepository.findLastAudit(doc.id(), implementationName())
            .defaultIfEmpty(DocumentHistory.builder().retry(0).build())
            .flatMap(current -> {
                int retry = current.retry() != null ? current.retry() + 1 : 1;
                String newState = retry >= MAX_RETRIES ? ProductState.FAILED : ProductState.PENDING;

                documentRepository.updateStateById(doc.id(), newState, LocalDateTime.now()).subscribe();

                DocumentHistory trace = DocumentHistory.builder()
                    .documentId(doc.id())
                    .filename(traceFilename)
                    .operation(implementationName())
                    .result("FAILURE")
                    .errorCode(errorCode)
                    .errorMessage(errorMsg)
                    .stackTrace(stackTrace)
                    .retry(retry)
                    .startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
                historyRepository.save(trace).subscribe();

                return Mono.just(FileUploadResult.builder()
                    .status(DocumentStatus.FAILURE.name())
                    .errorCode(errorCode)
                    .message(errorMsg)
                    .processedAt(Instant.now())
                    .success(false)
                    .attemptCount(retry)
                    .build());
            });
    }

    private static String getStackTrace(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

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
        log.log(Level.SEVERE, "Upload failed with errorCode={0}: {1}", new Object[]{errorCode, error.getMessage()});
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
