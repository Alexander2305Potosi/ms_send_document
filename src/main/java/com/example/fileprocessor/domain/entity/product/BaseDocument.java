package com.example.fileprocessor.domain.entity.product;

/**
 * Common interface for all processed documents (e.g. Products, Animals).
 */
public interface BaseDocument {
    Long getId();
    String getDocumentId();
    String getProductId(); // Represents the parent entity ID (productId, animalId, etc.)
    String getName();
    String getState();
    Boolean getIsZip();
    String getUseCase();
    int getRetryCountSafe();
    String getHomologationFolder();
    String getHomologationCountry();
    String getCategoriaHomologada();
    String getSucursal();
}
