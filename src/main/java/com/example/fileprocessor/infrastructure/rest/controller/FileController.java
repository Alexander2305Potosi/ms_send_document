package com.example.fileprocessor.infrastructure.rest.controller;

import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final ProcessFileUseCase processFileUseCase;

    public FileController(ProcessFileUseCase processFileUseCase) {
        this.processFileUseCase = processFileUseCase;
    }

    @GetMapping(value = "/{documentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncProcessResponse> getFile(@PathVariable String documentId) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Received request to process document: {}, traceId: {}", documentId, traceId);

        processFileUseCase.execute(documentId, traceId)
            .doOnNext(result -> log.info("Document {} processing completed: status={}, correlationId={}",
                documentId, result.getStatus(), result.getCorrelationId()))
            .doOnError(error -> log.error("Document {} processing failed: {}", documentId, error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new AsyncProcessResponse(
                "PROCESSING",
                "Document processing started",
                traceId,
                traceId,
                null,
                Instant.now(),
                null,
                true
            ));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncProcessResponse> getAllFiles(
            @RequestParam(required = false) String traceId) {

        String actualTraceId = traceId != null ? traceId : UUID.randomUUID().toString();
        MDC.put("traceId", actualTraceId);

        log.info("Received request to process all documents, traceId: {}", actualTraceId);

        processFileUseCase.executeAll(actualTraceId)
            .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                result.getCorrelationId(), result.getStatus()))
            .doOnError(error -> log.error("Processing failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new AsyncProcessResponse(
                "PROCESSING",
                "All documents processing started",
                actualTraceId,
                actualTraceId,
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