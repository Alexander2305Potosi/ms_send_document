package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Main domain entity representing the 'documentos' table.
 * Acts as a state machine for the document lifecycle.
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
    private String syncMessage;
    private Boolean isZip;
    private String useCase;
    private String originFolder;
    private String originCountry;
    private String homologationFolder;
    private String homologationCountry;
    private String categoriaHomologada;
    private LocalDateTime createdAt;
    private Integer retryCount;

    public int getRetryCountSafe() {
        return retryCount != null ? retryCount : 0;
    }

    public boolean isZipSafe() {
        return isZip != null && isZip;
    }
}
