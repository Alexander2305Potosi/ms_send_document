package com.example.fileprocessor.domain.entity.animal;

import com.example.fileprocessor.domain.entity.product.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Domain entity representing the animal documents.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimalDocument implements BaseDocument {
    public static final String USE_CASE_NAME = "Animal";
    
    private Long id;
    private String documentId;
    private String animalId;
    private String raza;
    private String tipo;
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
    private String sucursal;
    private LocalDateTime createdAt;
    private Integer retryCount;

    @Override
    public String getProductId() {
        return animalId; // Satisfy BaseDocument contract if needed elsewhere
    }

    @Override
    public int getRetryCountSafe() {
        return retryCount != null ? retryCount : 0;
    }

    public boolean isZipSafe() {
        return isZip != null && isZip;
    }
}
