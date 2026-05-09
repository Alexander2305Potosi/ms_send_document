package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base use case for document processing with daily load resilience, 
 * 3-retry policy and transactional tracking.
 */
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger log = Logger.getLogger(getClass().getName());

    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;
    private final DocumentHistoryRepository historyRepository;
    private final TransactionalOperator transactionalOperator;

    protected AbstractDocumentProcessingUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator,
            DocumentHistoryRepository historyRepository,
            TransactionalOperator transactionalOperator) {
        this.documentRepository = documentRepository;
        this.productRestGateway = productRestGateway;
        this.documentValidator = documentValidator;
        this.historyRepository = historyRepository;
        this.transactionalOperator = transactionalOperator;
    }

    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        return documentRepository.resetStaleDocumentsToday(implementationName(), startOfDay, threshold)
            .doOnNext(count -> {
                if (count > 0) log.warning("[" + implementationName() + "] Recovered " + count + " stale documents from today.");
            })
            .thenMany(documentRepository.findByStateAndUseCaseToday(ProductState.PENDING, implementationName(), startOfDay))
            .concatMap(this::processWithTracking)
            .doOnTerminate(() -> log.log(Level.INFO, "[{0}] Daily pipeline execution completed", implementationName()));
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc) {
        final Instant startTime = Instant.now();
        
        return documentRepository.updateStateAndRetry(doc.id(), ProductState.PENDING, ProductState.IN_PROGRESS, 
                                                    doc.getRetryCountSafe(), LocalDateTime.now())
            .flatMap(rows -> {
                if (rows == 0) return Mono.empty(); 
                
                log.log(Level.INFO, "[{0}] Started processing document {1} for product {2}", 
                        new Object[]{implementationName(), doc.documentId(), doc.productId()});
                
                return productRestGateway.getDocument(doc.productId(), doc.documentId())
                    .map(ProductDocumentHistory::from)
                    .flatMapMany(this::decompress)
                    .concatMap(file -> documentValidator.validate(file, true)
                        .onErrorResume(ProcessingException.class, e -> handleBusinessRuleSkip(doc, e))
                        .flatMap(v -> uploadDocument(v, doc.productId(), doc.id()))
                    )
                    .next()
                    .onErrorResume(error -> handleGlobalError(error, doc))
                    .flatMap(response -> finalizeProcessing(doc, response, startTime));
            });
    }

    private Mono<FileUploadResponse> finalizeProcessing(Document doc, FileUploadResponse response, Instant startTime) {
        String finalState;
        int nextRetryCount = doc.getRetryCountSafe();

        if (response.isSuccess()) {
            finalState = ProductState.PROCESSED;
        } else if (isTransientError(response.getErrorCode()) && nextRetryCount < 3) {
            finalState = ProductState.PENDING; // Reintento técnico
            nextRetryCount++;
            log.log(Level.WARNING, "[{0}] Technical error. Retrying document {1} (Attempt {2}/3)", 
                    new Object[]{implementationName(), doc.documentId(), nextRetryCount});
        } else {
            finalState = ProductState.FAILED; // Fallo de negocio o reintentos agotados
            log.log(Level.SEVERE, "[{0}] Failed to process document {1} permanently. State set to FAILED.", 
                    new Object[]{implementationName(), doc.documentId()});
        }

        log.log(Level.INFO, "[{0}] Finished processing document {1}. Final State: {2}", 
                new Object[]{implementationName(), doc.documentId(), finalState});

        final int finalRetryCount = nextRetryCount;
        String historyFileName = Boolean.TRUE.equals(doc.isZip()) ? doc.name() : null;
        return historyRepository.saveHistory(doc.id(), historyFileName, implementationName(), response, startTime)
            .then(documentRepository.updateStateAndRetry(doc.id(), ProductState.IN_PROGRESS, finalState, 
                                                       finalRetryCount, LocalDateTime.now()))
            .as(transactionalOperator::transactional) // TODO O NADA
            .thenReturn(response);
    }

    private boolean isTransientError(String errorCode) {
        return java.util.Set.of(
            ProcessingResultCodes.BAD_GATEWAY.name(),
            ProcessingResultCodes.GATEWAY_TIMEOUT.name(),
            ProcessingResultCodes.SERVICE_UNAVAILABLE.name(),
            ProcessingResultCodes.UNKNOWN_ERROR.name()
        ).contains(errorCode);
    }

    private Mono<ProductDocumentHistory> handleBusinessRuleSkip(Document doc, ProcessingException e) {
        log.log(Level.INFO, "[{0}] Business rule skip for document {1}: {2}", 
                new Object[]{implementationName(), doc.documentId(), e.getMessage()});
        
        FileUploadResponse skipResponse = buildErrorResponse(e.getErrorCode(), "Business Rule: " + e.getMessage());
        return finalizeProcessing(doc, skipResponse, Instant.now()).then(Mono.empty());
    }

    private Mono<FileUploadResponse> handleGlobalError(Throwable error, Document doc) {
        String errorCode = error instanceof ProcessingException pe ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR.name();
        log.log(Level.SEVERE, "[{0}] Pipeline error for document {1} (Product: {2}): {3}", 
                new Object[]{implementationName(), doc.documentId(), doc.productId(), error.getMessage()});
        return Mono.just(buildErrorResponse(errorCode, error.getMessage()));
    }

    private FileUploadResponse buildErrorResponse(String errorCode, String message) {
        return FileUploadResponse.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .message(message)
            .processedAt(Instant.now())
            .success(false)
            .build();
    }

    private Flux<ProductDocumentHistory> decompress(ProductDocumentHistory file) {
        if (!file.isZip() || file.filename() == null || file.filename().isBlank()) {
            return Flux.just(file);
        }
        return ZipDecompressor.decompress(file)
            .onErrorMap(error -> new ProcessingException(
                "Failed to decompress ZIP '" + file.filename() + "': " + error.getMessage(),
                ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
                "unknown",
                error));
    }

    protected abstract Mono<FileUploadResponse> uploadDocument(ProductDocumentHistory doc, String productId, Long docId);

    protected abstract String implementationName();
}
