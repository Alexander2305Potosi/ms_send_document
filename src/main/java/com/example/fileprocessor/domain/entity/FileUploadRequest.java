package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Request object for file upload operations.
 */
@Getter
@Builder
public class FileUploadRequest {
    private String documentId;
    private byte[] content;
    private String filename;
    private String contentType;
    private long fileSize;
    private String origin;
    private String paisHomologado;
    private String subTipoDocumental;
    private Long docId;

    public static FileUploadRequest from(ProductDocumentHistory doc, Long docId) {
        return FileUploadRequest.builder()
            .documentId(doc.getDocumentId())
            .content(doc.getContent() != null ? doc.getContent() : new byte[0])
            .filename(doc.getFilename())
            .contentType(doc.getContentType())
            .fileSize(doc.getSize())
            .origin(doc.getOrigin())
            .docId(docId)
            .build();
    }
}
