package com.example.fileprocessor.domain.entity;

import java.time.Instant;

/**
 * Encapsula los datos necesarios para finalizar el procesamiento de un
 * documento.
 */
public record FinalizeProcessingCommand(
        Document document,
        FileUploadResponse response,
        String finalState,
        int nextRetryCount,
        Instant startTime) {
}