package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.DocumentToProcess;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Use case for loading documents from external REST API and saving them
 * to the database for later processing.
 */
public class LoadDocumentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoadDocumentsUseCase.class);

    private final DocumentRestGateway documentGateway;
    private final DocumentRepository documentRepository;

    public LoadDocumentsUseCase(DocumentRestGateway documentGateway, DocumentRepository documentRepository) {
        this.documentGateway = documentGateway;
        this.documentRepository = documentRepository;
    }

    /**
     * Loads all available documents from the external REST API and saves them
     * to the database for later processing.
     *
     * @return Flux<LoadDocumentsResult> with the result of loading each document
     */
    public Flux<LoadDocumentsResult> execute() {
        String traceId = java.util.UUID.randomUUID().toString();
        log.info("Loading documents from REST API, traceId: {}", traceId);

        return documentGateway.getAllDocuments(traceId)
            .flatMap(doc -> saveDocument(doc, traceId))
            .doOnNext(result -> log.info("Document loaded: {} -> {}", result.getDocumentId(), result.getStatus()))
            .doOnError(error -> log.error("Error loading documents: {}", error.getMessage()));
    }

    private Mono<LoadDocumentsResult> saveDocument(DocumentInfo doc, String traceId) {
        DocumentToProcess toProcess = toDocumentToProcess(doc, traceId);

        return documentRepository.save(toProcess)
            .thenReturn(LoadDocumentsResult.builder()
                .documentId(doc.getDocumentId())
                .filename(doc.getFilename())
                .status("LOADED")
                .message("Document saved for processing")
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(true)
                .build())
            .onErrorResume(error -> {
                log.error("Failed to save document {}: {}", doc.getDocumentId(), error.getMessage());
                return Mono.just(LoadDocumentsResult.builder()
                    .documentId(doc.getDocumentId())
                    .filename(doc.getFilename())
                    .status("FAILED")
                    .message("Failed to save: " + error.getMessage())
                    .traceId(traceId)
                    .processedAt(Instant.now())
                    .success(false)
                    .build());
            });
    }

    private DocumentToProcess toDocumentToProcess(DocumentInfo doc, String traceId) {
        return DocumentToProcess.builder()
            .documentId(doc.getDocumentId())
            .filename(doc.getFilename())
            .origin(doc.getOrigin() != null ? doc.getOrigin() : "")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .traceId(traceId)
            .build();
    }
}
