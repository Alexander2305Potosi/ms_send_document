package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Domain entity representing the 'historico_documentos' table.
 * Contains ONLY fields that map to the database.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistory {
    private Long id;
    private Long documentId; // FK to documentos.id
    private String filename;
    private String useCase;
    private String result;
    private String errorCode;
    private String syncMessage;
    private Integer retry;
    private Instant startedAt;
    private Instant completedAt;
}
