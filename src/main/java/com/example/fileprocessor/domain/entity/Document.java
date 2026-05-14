package com.example.fileprocessor.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Main domain entity representing a document in the processing pipeline.
 * Redundant fields (active, owner, path, updatedAt) have been removed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    private Long id;
    private String documentId;
    private String productId;
    private String name;
    private String state;
    private String errorMessage;
    private boolean isZip;
    private String useCase;
    private LocalDateTime createdAt;
    private Integer retryCount;

    public int getRetryCountSafe() {
        return retryCount != null ? retryCount : 0;
    }
}
