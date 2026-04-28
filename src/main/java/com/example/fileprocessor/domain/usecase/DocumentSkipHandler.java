package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Handles document skipping logic based on business rules.
 * Encapsulates the decision logic for when a document should be skipped
 * due to folder rules, origin patterns, or size limits.
 */
public class DocumentSkipHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentSkipHandler.class);

    private final ProductDocumentRepository documentRepository;
    private final ProductStatusAggregator statusAggregator;

    public DocumentSkipHandler(ProductDocumentRepository documentRepository,
                              ProductStatusAggregator statusAggregator) {
        this.documentRepository = documentRepository;
        this.statusAggregator = statusAggregator;
    }

    /**
     * Skips a document and updates its status to the appropriate terminal state.
     */
    public Mono<FileUploadResult> skipDocument(ProductDocumentToProcess pending, String traceId,
            String status, String message, String errorCode, String externalReference) {
        log.info("Document {} skipped: status={}, errorCode={}", pending.getDocumentId(), status, errorCode);
        return documentRepository.updateStatus(pending.getDocumentId(), status, traceId, null, errorCode)
            .flatMap(v -> statusAggregator.updateProductStatus(pending.getProductId(), traceId))
            .thenReturn(buildSkippedResult(status, message, traceId, externalReference));
    }

    private FileUploadResult buildSkippedResult(String status, String message, String traceId, String externalReference) {
        return FileUploadResult.builder()
            .status(status)
            .message(message)
            .traceId(traceId)
            .processedAt(Instant.now())
            .externalReference(externalReference)
            .success(true)
            .build();
    }
}
