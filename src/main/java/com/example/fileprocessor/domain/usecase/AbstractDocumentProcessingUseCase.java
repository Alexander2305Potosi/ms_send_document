package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = Logger.getLogger(getClass().getName());

    protected final DocumentHistoryRepository historyRepository;
    protected final ProductRestGateway productRestGateway;
    protected final RulesBussinesGateway documentValidator;

    private static final int MAX_RETRIES = 3;

    public Flux<FileUploadResult> executePendingDocuments() {
        return historyRepository.findByStateAndUseCase(ProductState.PENDING, implementationName())
            .groupBy(DocumentHistory::documentId)
            .flatMap(group -> group.reduce((latest, current) ->
                latest.createdAt() != null && current.createdAt() != null
                && latest.createdAt().isAfter(current.createdAt()) ? latest : current))
            .flatMap(doc -> {
                String documentId = doc.documentId();
                return canResume(documentId, doc.useCase())
                    .flatMapMany(resumeable -> {
                        if (!resumeable) {
                            log.log(Level.INFO, "Document {0} already processed, skipping", documentId);
                            return Flux.empty();
                        }
                        historyRepository.updateStateAndUseCase(documentId, ProductState.IN_PROGRESS, implementationName()).subscribe();
                        return processDocument(doc);
                    });
            })
            .doOnTerminate(() -> log.log(Level.INFO, "Pipeline {0} completed", new Object[]{implementationName()}))
            .doOnError(e -> log.log(Level.SEVERE, "Pipeline error: {0}", new Object[]{e.getMessage()}))
            .doOnCancel(() -> log.log(Level.WARNING, "Pipeline {0} cancelled", new Object[]{implementationName()}));
    }

    private Mono<Boolean> canResume(String documentId, String useCase) {
        return historyRepository.findLastAudit(documentId, useCase)
            .map(lastAudit -> {
                if (lastAudit == null) return true;
                return !ProductState.PROCESSED.equals(lastAudit.state());
            })
            .defaultIfEmpty(true);
    }

    private Flux<FileUploadResult> processDocument(DocumentHistory doc) {
        String documentId = doc.documentId();
        return productRestGateway.getDocument(doc.productId(), documentId)
            .map(file -> toProductDocument(file))
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
                    historyRepository.updateStateAndUseCase(documentId, ProductState.PROCESSED, implementationName()).subscribe();
                    return Mono.empty();
                })))
            .flatMap(validated -> uploadDocument(validated, doc.productId()))
            .flatMap(result -> handleUploadSuccess(doc, result))
            .onErrorResume(error -> handleUploadError(doc, error));
    }

    private Mono<FileUploadResult> handleUploadSuccess(DocumentHistory doc, FileUploadResult result) {
        historyRepository.updateWithAudit(doc.documentId(), ProductState.PROCESSED, null, null, 0, implementationName()).subscribe();
        return Mono.just(result);
    }

    private Mono<FileUploadResult> handleUploadError(DocumentHistory doc, Throwable error) {
        String errorCode = error instanceof ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        String errorMsg = error.getMessage();

        return historyRepository.findLastAudit(doc.documentId())
            .defaultIfEmpty(DocumentHistory.builder().retry(0).build())
            .flatMap(current -> {
                int retry = current.retry() != null ? current.retry() + 1 : 1;
                String newState = retry >= MAX_RETRIES ? ProductState.FAILED : ProductState.PENDING;

                historyRepository.updateWithAudit(doc.documentId(), newState, errorCode, errorMsg, retry, implementationName()).subscribe();

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
