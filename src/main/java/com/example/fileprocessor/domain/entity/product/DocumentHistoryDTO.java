package com.example.fileprocessor.domain.entity.product;

import com.example.fileprocessor.domain.util.SanitizationUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * DTO for transporting product document processing audit information.
 */
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistoryDTO extends BaseDocumentHistoryDTO {
    
    private String productId;

    @Override
    public String getProductId() {
        return productId;
    }

    /**
     * Factory method to initialize DTO from the base Document.
     */
    public static DocumentHistoryDTO fromDocument(BaseDocument doc) {
        return DocumentHistoryDTO.builder()
                .documentId(doc.getId())
                .businessDocumentId(SanitizationUtils.sanitizeKeepingText(doc.getDocumentId()))
                .productId(doc.getProductId())
                .filename(doc.getName())
                .useCase(doc.getUseCase())
                .retryCount(doc.getRetryCountSafe())
                .businessRetryCount(doc.getRetryCountSafe())
                .isZip(doc.getIsZip())
                .startedAt(Instant.now())
                .homologationFolder(doc.getHomologationFolder())
                .homologationCountry(doc.getHomologationCountry())
                .categoriaHomologada(doc.getCategoriaHomologada())
                .build();
    }

    @Override
    public DocumentHistoryDTO clone() {
        return this.toBuilder().build();
    }
}
