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
            .documentId(doc.documentId())
            .content(doc.content() != null ? doc.content() : new byte[0])
            .filename(doc.filename())
            .contentType(doc.contentType())
            .fileSize(doc.size())
            .origin(doc.origin())
            .docId(docId)
            .build();
    }
}
