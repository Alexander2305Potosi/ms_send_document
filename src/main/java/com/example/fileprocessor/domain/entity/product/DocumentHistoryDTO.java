package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for transporting document processing audit information and transient metadata.
 * Carries all information needed for validations and processing.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistoryDTO {
    // Database matching fields
    private Long documentId;
    private String state;
    private String useCase;
    private Integer retryCount;
    private String filename;
    private String errorCode;
    private String syncMessage;
    private Instant startedAt;
    private Instant completedAt;

    // Extra information for processing and validations
    private String productId;
    private String businessDocumentId; // The String ID (id_documento)
    private String contentType;
    private Long size;
    private String origin;
    private String pais;
    private byte[] content;
    private Boolean isZip;

    /**
     * Factory method to initialize DTO from the base Document.
     */
    public static DocumentHistoryDTO fromDocument(Document doc) {
        return DocumentHistoryDTO.builder()
                .documentId(doc.getId())
                .businessDocumentId(doc.getDocumentId())
                .productId(doc.getProductId())
                .filename(doc.getName())
                .useCase(doc.getUseCase())
                .retryCount(doc.getRetryCountSafe())
                .isZip(doc.getIsZip())
                .startedAt(Instant.now())
                .build();
    }
}
