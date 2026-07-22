package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Request object for file upload operations with optional homologation support.
 * Uses BaseDocumentHistoryDTO to extract transient processing metadata.
 */
@Getter
@Setter
@Builder
public class FileUploadRequest {
    private String documentId;
    private byte[] content;
    private String filename;
    private String contentType;
    private long fileSize;
    private String originFolder;
    private String categoriaDocument;
    private String homologationFolder;
    private String homologationCountry;
    private Long docId;
    private String useCase;

    // Optional fields per use case (Animal)
    private String animalId;
    private String raza;
    private String tipo;

    public static FileUploadRequest from(BaseDocumentHistoryDTO history, byte[] content, Long docId, HomologationResult h) {
        return FileUploadRequest.builder()
            .documentId(history.getBusinessDocumentId())
            .content(content != null ? content : new byte[0])
            .filename(history.getFilename())
            .contentType(history.getContentType())
            .fileSize(history.getSize() != null ? history.getSize() : 0)
            .originFolder(history.getOriginFolder())
            .categoriaDocument(h != null ? h.categoriaDocument() : history.getBusinessDocumentId())
            .homologationFolder(h != null && h.homologationCountry() != null ? h.homologationCountry().homologationFolder() : history.getOriginFolder())
            .homologationCountry(h != null && h.homologationCountry() != null ? h.homologationCountry().homologationCountry() : history.getOriginCountry())
            .docId(docId)
            .useCase(history.getUseCase())
            .build();
    }

    /**
     * Factory method for Animal use case — maps animal-specific fields.
     */
    public static FileUploadRequest fromAnimal(AnimalDocumentHistoryDTO history, byte[] content, Long docId,
                                                HomologationResult h) {
        return FileUploadRequest.builder()
            .documentId(history.getBusinessDocumentId())
            .content(content != null ? content : new byte[0])
            .filename(history.getFilename())
            .contentType(history.getContentType())
            .fileSize(history.getSize() != null ? history.getSize() : 0)
            .originFolder(history.getOriginFolder())
            .categoriaDocument(h != null ? h.categoriaDocument() : history.getBusinessDocumentId())
            .homologationFolder(h != null && h.homologationCountry() != null ? h.homologationCountry().homologationFolder() : history.getOriginFolder())
            .homologationCountry(h != null && h.homologationCountry() != null ? h.homologationCountry().homologationCountry() : history.getOriginCountry())
            .docId(docId)
            .useCase(history.getUseCase())
            .animalId(history.getAnimalId())
            .raza(history.getRaza())
            .tipo(history.getTipo())
            .build();
    }
}

