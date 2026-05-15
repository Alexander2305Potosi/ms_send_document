package com.example.fileprocessor.domain.entity.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Domain entity representing the 'historico_documentos' table.
 * Now acts as the main carrier for processing information throughout the use case.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistory {
    // Database Fields (historico_documentos)
    private Long id;
    private Long documentId; // FK to documentos.id
    private String filename;
    private String operation;
    private String result;
    private String errorCode;
    private String errorMessage;
    private String stackTrace;
    private Integer retry;
    private Instant startedAt;
    private Instant completedAt;

    // Transient Processing Fields (to carry info during use case)
    private String productId;
    private String businessDocumentId; // The String ID (id_documento)
    private String contentType;
    private Long size;
    private String origin;
    private String pais;
    private byte[] content;
    private boolean isZip;

    /**
     * Factory method to initialize history from the base Document.
     */
    public static DocumentHistory fromDocument(Document doc) {
        return DocumentHistory.builder()
            .documentId(doc.getId())
            .businessDocumentId(doc.getDocumentId())
            .productId(doc.getProductId())
            .filename(doc.getName())
            .retry(doc.getRetryCountSafe())
            .isZip(doc.isZip())
            .startedAt(Instant.now())
            .build();
    }
}
