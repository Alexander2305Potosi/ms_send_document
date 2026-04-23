package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port for fetching documents from external REST API.
 */
public interface DocumentRestGateway {

    /**
     * Fetches a document by its ID from the external REST API.
     *
     * @param documentId the unique identifier of the document
     * @param traceId the trace ID for logging purposes
     * @return Mono<DocumentInfo> with the document data
     */
    Mono<DocumentInfo> getDocument(String documentId, String traceId);

    /**
     * Fetches all available documents from the external REST API.
     *
     * @param traceId the trace ID for logging purposes
     * @return Flux<DocumentInfo> with all available documents
     */
    Flux<DocumentInfo> getAllDocuments(String traceId);
}