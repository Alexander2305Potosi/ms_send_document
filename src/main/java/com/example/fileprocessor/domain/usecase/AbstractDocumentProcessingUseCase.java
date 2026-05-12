package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.DocumentUpdateCommand;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ExceptionMapper;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base use case for document processing. Restored to use ProductRestGateway.
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
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        return persistencePort.resetStaleDocumentsToday(implementationName(), startOfDay, threshold)
            .thenMany(persistencePort.findPendingDocumentsToday(implementationName(), startOfDay))
            .concatMap(this::processWithTracking);
    }

    private Mono<FileUploadResponse> processWithTracking(Document doc) {
        final Instant startTime = Instant.now();
        
        return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .flatMap(rows -> {
                if (rows == 0) return Mono.empty(); 
                
                // RESTAURADO: Se sigue consultando el contenido vía API REST para SOAP/S3
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
            finalState = ProductState.PENDING;
            nextRetryCount++;
        } else {
            finalState = ProductState.FAILED;
        }

        DocumentUpdateCommand command = DocumentUpdateCommand.finalize(
            doc, response, finalState, nextRetryCount, startTime
        );

        return persistencePort.finalizeProcessingAtomically(command);
    }

    private boolean isTransientError(String errorCode) {
        if(errorCode == null) return false;
        return java.util.Set.of(
            ProcessingResultCodes.BAD_GATEWAY.name(),
            ProcessingResultCodes.GATEWAY_TIMEOUT.name(),
            ProcessingResultCodes.SERVICE_UNAVAILABLE.name(),
            ProcessingResultCodes.UNKNOWN_ERROR.name()
        ).contains(errorCode);
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
        ExceptionMapper.ErrorClassification classification = ExceptionMapper.classify(error);
        return Mono.just(FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .errorCode(classification.code())
            .message(classification.message())
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
