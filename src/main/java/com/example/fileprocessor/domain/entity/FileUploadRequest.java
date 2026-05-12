package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Request object for file upload operations with optional homologation support.
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

    public static FileUploadRequest from(ProductDocumentHistory doc, Long docId, HomologationResult h) {
        return FileUploadRequest.builder()
            .documentId(doc.getDocumentId())
            .content(doc.getContent() != null ? doc.getContent() : new byte[0])
            .filename(doc.getFilename())
            .contentType(doc.getContentType())
            .fileSize(doc.getSize())
            // Si h es nulo (caso S3), usamos los valores originales del documento
            .origin(h != null ? h.origin() : doc.getOrigin())
            .paisHomologado(h != null ? h.paisHomologado() : doc.getPais())
            .docId(docId)
            .build();
    }
}
