package com.example.fileprocessor.domain.entity.animal;

import com.example.fileprocessor.domain.entity.product.BaseDocument;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import com.example.fileprocessor.domain.util.SanitizationUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * DTO for transporting animal document processing audit information.
 */
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AnimalDocumentHistoryDTO extends BaseDocumentHistoryDTO {
    
    private String animalId;
    private String raza;
    private String tipo;

    @Override
    public String getProductId() {
        return animalId; // Maps parent entity ID to animalId
    }

    /**
     * Factory method to initialize DTO from the AnimalDocument entity.
     */
    public static AnimalDocumentHistoryDTO fromDocument(AnimalDocument doc) {
        return AnimalDocumentHistoryDTO.builder()
                .documentId(doc.getId())
                .businessDocumentId(SanitizationUtils.sanitizeKeepingText(doc.getDocumentId()))
                .animalId(doc.getAnimalId()) 
                .raza(doc.getRaza())
                .tipo(doc.getTipo())
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
    public AnimalDocumentHistoryDTO clone() {
        return this.toBuilder().build();
    }
}
