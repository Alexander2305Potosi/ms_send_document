package com.example.fileprocessor.domain.entity;

import java.time.Instant;

/**
 * Command containing all data needed to record a document processing history entry.
 */
public record SaveHistoryCommand(
    Long docId,
    String filename,
    String operation,
    FileUploadResponse response,
    int retryCount,
    Instant startTime
) {}
