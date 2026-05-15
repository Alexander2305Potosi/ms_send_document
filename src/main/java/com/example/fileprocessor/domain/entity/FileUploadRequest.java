package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Request object for file upload operations with optional homologation support.
 * Uses DocumentHistoryDTO to extract transient processing metadata.
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
    private String origin;
    private String paisHomologado;
    private Long docId;

    public static FileUploadRequest from(DocumentHistoryDTO history, Long docId, HomologationResult h) {
        return FileUploadRequest.builder()
            .documentId(history.getBusinessDocumentId())
            .content(history.getContent() != null ? history.getContent() : new byte[0])
            .filename(history.getFilename())
            .contentType(history.getContentType())
            .fileSize(history.getSize() != null ? history.getSize() : 0)
            .origin(h != null ? h.origin() : history.getOrigin())
            .paisHomologado(h != null ? h.paisHomologado() : history.getPais())
            .docId(docId)
            .build();
    }
}
