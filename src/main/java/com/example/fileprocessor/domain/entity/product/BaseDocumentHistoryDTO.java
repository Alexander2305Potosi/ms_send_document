package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@lombok.Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDocumentHistoryDTO {
    // Database matching fields
    private Long documentId;
    private String state;
    private String useCase;
    private Integer retryCount;
    private Integer businessRetryCount;
    private String filename;
    private String syncStatus;
    private String syncMessage;
    private Instant startedAt;
    private Instant completedAt;
    private String homologationFolder;
    private String homologationCountry;
    private String categoriaHomologada;

    // Extra information for processing and validations
    private String businessDocumentId; // The String ID (id_documento)
    private String contentType;
    private Long size;
    private String originFolder;
    private String originCountry;
    private Boolean isZip;

    public abstract String getProductId(); // Abstract getter for parent entity ID
    
    @Override
    public abstract BaseDocumentHistoryDTO clone();
}
