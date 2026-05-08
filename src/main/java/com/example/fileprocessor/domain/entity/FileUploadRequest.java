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
}
