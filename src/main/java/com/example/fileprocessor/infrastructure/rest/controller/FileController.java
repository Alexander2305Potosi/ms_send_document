package com.example.fileprocessor.infrastructure.rest.controller;

import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncProcessResponse> processPendingDocuments() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Starting pending documents processing, traceId: {}", traceId);

        processFileUseCase.executePendingDocuments()
            .doOnNext(result -> log.info("Document processed: correlationId={}, status={}",
                result.getCorrelationId(), result.getStatus()))
            .doOnError(error -> log.error("Processing failed: {}", error.getMessage()))
            .doFinally(signal -> MDC.remove("traceId"))
            .subscribe();

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new AsyncProcessResponse(
                "PROCESSING",
                "Pending documents processing started",
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
