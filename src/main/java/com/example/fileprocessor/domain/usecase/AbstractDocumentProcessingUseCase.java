package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Base use case for document processing.
 * Clean Architecture compliant: No infrastructure or technical utility dependencies.
 */
public abstract class AbstractDocumentProcessingUseCase {

    protected final Logger LOGGER = Logger.getLogger(getClass().getName());

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    protected AbstractDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator) {
        this.persistencePort = persistencePort;
        this.productRestGateway = productRestGateway;
        this.documentValidator = documentValidator;
    }

    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return persistencePort.findPendingDocumentsToday(implementationName(), startOfDay)
            .concatMap(this::processWithTracking);
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc) {
        final Instant startTime = Instant.now();
        
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .flatMap(rows -> {
                if (rows == 0) return Mono.empty(); 
                
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
        // Business logic for state transitions and retries
        final int MAX_RETRIES = 3;
        final int currentRetry = doc.getRetryCountSafe();
        final boolean isRetryable = ProcessingResultCodes.isTransient(response.getErrorCode()) && currentRetry < MAX_RETRIES;

        String nextState;
        if (response.isSuccess()) {
            nextState = ProductState.PROCESSED;
        } else if (isRetryable) {
            nextState = ProductState.PENDING;
            doc.setRetryCount(currentRetry + 1);
        } else {
            nextState = ProductState.FAILED;
        }

        // Update the main aggregate state
        doc.setState(nextState);
        doc.setErrorMessage(response.getMessage());

        // Create the Audit DTO
        DocumentHistoryDTO historyDTO = DocumentHistoryDTO.builder()
            .errorCode(response.getErrorCode())
            .errorMessage(response.getMessage())
            .startedAt(startTime)
            .completedAt(Instant.now())
            .build();

        return persistencePort.finalizeProcessingAtomically(doc, historyDTO)
            .thenReturn(response);
    }


    private Mono<ProductDocumentHistory> handleBusinessRuleSkip(Document doc, ProcessingException e) {
        FileUploadResponse skipResponse = FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .errorCode(e.getErrorCode())
            .message("Business Rule: " + e.getMessage())
            .processedAt(Instant.now())
            .success(false)
            .build();
        return finalizeProcessing(doc, skipResponse, Instant.now()).then(Mono.empty());
    }

    private Mono<FileUploadResponse> handleGlobalError(Throwable error, Document doc) {
        // En Clean Architecture, el caso de uso solo maneja excepciones de dominio (ProcessingException)
        // Cualquier otra cosa se trata como un error desconocido a nivel de negocio.
        String errorCode = ProcessingResultCodes.UNKNOWN_ERROR.name();
        String message = error.getMessage();

        if (error instanceof ProcessingException pe) {
            errorCode = pe.getErrorCode();
        }

        return Mono.just(FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .errorCode(errorCode)
            .message(message != null ? message : "Unexpected processing error")
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    private Flux<ProductDocumentHistory> decompress(ProductDocumentHistory file) {
        if (!file.isZip() || file.getFilename() == null || file.getFilename().isBlank()) {
            return Flux.just(file);
        }
        return ZipDecompressor.decompress(file);
    }

    protected abstract Mono<FileUploadResponse> uploadDocument(ProductDocumentHistory doc, String productId, Long docId);

    protected abstract String implementationName();
}
