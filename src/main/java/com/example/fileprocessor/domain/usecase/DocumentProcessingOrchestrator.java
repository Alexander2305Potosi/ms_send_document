package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates document processing using a pipeline-based approach.
 * Delegates to DocumentProcessingPipeline for the actual processing stages.
 *
 * Replaces AbstractProcessDocumentsUseCase with a composition-based architecture
 * that uses DocumentSender strategy for actual document sending.
 */
public class DocumentProcessingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingOrchestrator.class);

    private final ProductDocumentRepository documentRepository;
    private final DocumentProcessingPipeline pipeline;
    private final String implementationName;

    public DocumentProcessingOrchestrator(
            ProductDocumentRepository documentRepository,
            DocumentProcessingPipeline pipeline,
            String implementationName) {
        this.documentRepository = documentRepository;
        this.pipeline = pipeline;
        this.implementationName = implementationName;
    }

    public Flux<FileUploadResult> executePendingDocuments() {
        return documentRepository.findPendingDocuments()
            .flatMap(this::processPendingDocument, ProcessingMessages.DEFAULT_MAX_CONCURRENCY)
            .doOnNext(response -> log.info("Document processed: correlationId={}", response.getCorrelationId()))
            .doOnError(error -> log.error("Error processing document: {}", error.getMessage()));
    }

    private Mono<FileUploadResult> processPendingDocument(ProductDocumentToProcess pending) {
        String traceId = UUID.randomUUID().toString();
        return documentRepository.claimDocument(pending.getDocumentId())
            .filter(Boolean::booleanValue)
            .flatMap(claimed -> pipeline.process(pending, traceId))
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Document {} already claimed by another process or not pending, skipping",
                    pending.getDocumentId());
                return Mono.empty();
            }));
    }

    public String getImplementationName() {
        return implementationName;
    }
}