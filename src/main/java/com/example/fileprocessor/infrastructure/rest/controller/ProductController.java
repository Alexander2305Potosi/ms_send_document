package com.example.fileprocessor.infrastructure.rest.controller;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.usecase.ProcessProductDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProcessProductDocumentsUseCase processProductDocumentsUseCase;
    private final LoadProductsUseCase loadProductsUseCase;

    public ProductController(ProcessProductDocumentsUseCase processProductDocumentsUseCase,
                            LoadProductsUseCase loadProductsUseCase) {
        this.processProductDocumentsUseCase = processProductDocumentsUseCase;
        this.loadProductsUseCase = loadProductsUseCase;
    }

    @GetMapping(value = "/load", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncProcessResponse> loadProducts() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Starting products load from REST API, traceId: {}", traceId);

        loadProductsUseCase.execute()
            .doOnNext(result -> log.info("Product loaded: {} -> {} ({} documents)",
                result.getProductId(), result.getStatus(), result.getDocumentCount()))
            .doOnError(error -> log.error("Load failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new AsyncProcessResponse(
                "LOADING",
                "Product loading from REST API started",
                null,
                traceId,
                null,
                Instant.now(),
                null,
                true
            ));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncProcessResponse> processPendingProducts() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Starting pending product documents processing, traceId: {}", traceId);

        processProductDocumentsUseCase.executePendingDocuments()
            .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                result.getCorrelationId(), result.getStatus()))
            .doOnError(error -> log.error("Processing failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new AsyncProcessResponse(
                DocumentStatus.PROCESSING_VALUE,
                "Pending product documents processing started",
                null,
                traceId,
                null,
                Instant.now(),
                null,
                true
            ));
    }

    public record AsyncProcessResponse(
        String status,
        String message,
        String correlationId,
        String traceId,
        String externalReference,
        Instant processedAt,
        String errorCode,
        boolean success
    ) {}
}
