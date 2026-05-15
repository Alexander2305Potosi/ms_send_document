package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for transporting document processing audit information.
 * Decouples the main Document entity from transient processing metadata.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistoryDTO {
    private String filename;
    private String errorCode;
    private String errorMessage;
    private String stackTrace;
    private Instant startedAt;
    private Instant completedAt;
}
