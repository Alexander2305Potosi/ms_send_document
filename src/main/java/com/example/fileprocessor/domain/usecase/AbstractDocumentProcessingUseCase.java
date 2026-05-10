package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FinalizeProcessingCommand;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base use case for document processing with daily load resilience, 
 * 3-retry policy and transactional tracking.
 * Pure domain logic (no infrastructure dependencies).
 */
@AllArgsConstructor
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger LOGGER = Logger.getLogger(getClass().getName());

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        return persistencePort.resetStaleDocumentsToday(implementationName(), startOfDay, threshold)
            .doOnNext(count -> {
                if (count > 0) LOGGER.warning("[" + implementationName() + "] Recovered " + count + " stale documents from today.");
            })
            .thenMany(persistencePort.findPendingDocumentsToday(implementationName(), startOfDay))
            .concatMap(this::processWithTracking)
            .doOnTerminate(() -> LOGGER.log(Level.INFO, "[{0}] Daily pipeline execution completed", implementationName()));
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc) {
        final Instant startTime = Instant.now();
        
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .flatMap(rows -> {
                if (rows == 0) return Mono.empty(); 
                
                LOGGER.log(Level.INFO, "[{0}] Started processing document {1} for product {2}", 
                        new Object[]{implementationName(), doc.getDocumentId(), doc.getProductId()});
                
                return productRestGateway.getDocument(doc.getProductId(), doc.getDocumentId())
                    .map(ProductDocumentHistory::from)
                    .flatMapMany(this::decompress)
                    .concatMap(file -> documentValidator.validate(file, true)
                        .onErrorResume(ProcessingException.class, e -> handleBusinessRuleSkip(doc, e))
                        .flatMap(v -> uploadDocument(v, doc.getProductId(), doc.getId()))
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
            LOGGER.log(Level.WARNING, "[{0}] Technical error. Retrying document {1} (Attempt {2}/3)", 
                    new Object[]{implementationName(), doc.getDocumentId(), nextRetryCount});
        } else {
            finalState = ProductState.FAILED; // Fallo de negocio o reintentos agotados
            LOGGER.log(Level.SEVERE, "[{0}] Failed to process document {1} permanently. State set to FAILED.", 
                    new Object[]{implementationName(), doc.getDocumentId()});
        }

        LOGGER.log(Level.INFO, "[{0}] Finished processing document {1}. Final State: {2}", 
                new Object[]{implementationName(), doc.getDocumentId(), finalState});

        FinalizeProcessingCommand command = new FinalizeProcessingCommand(
            doc, response, finalState, nextRetryCount, startTime
        );

        return persistencePort.finalizeProcessingAtomically(command);
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
        LOGGER.log(Level.INFO, "[{0}] Business rule skip for document {1}: {2}", 
                new Object[]{implementationName(), doc.getDocumentId(), e.getMessage()});
        
        FileUploadResponse skipResponse = buildErrorResponse(e.getErrorCode(), "Business Rule: " + e.getMessage());
        return finalizeProcessing(doc, skipResponse, Instant.now()).then(Mono.empty());
    }

    private Mono<FileUploadResponse> handleGlobalError(Throwable error, Document doc) {
        String errorCode = error instanceof ProcessingException pe ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR.name();
        LOGGER.log(Level.SEVERE, "[{0}] Pipeline error for document {1} (Product: {2}): {3}", 
                new Object[]{implementationName(), doc.getDocumentId(), doc.getProductId(), error.getMessage()});
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
        if (!file.isZip() || file.getFilename() == null || file.getFilename().isBlank()) {
            return Flux.just(file);
        }
        return ZipDecompressor.decompress(file)
            .onErrorMap(error -> new ProcessingException(
                "Failed to decompress ZIP '" + file.getFilename() + "': " + error.getMessage(),
                ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
                "unknown",
                error));
    }

    protected abstract Mono<FileUploadResponse> uploadDocument(ProductDocumentHistory doc, String productId, Long docId);

    protected abstract String implementationName();
}
