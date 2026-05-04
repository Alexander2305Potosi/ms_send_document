package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

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
    private LocalDate vigencia;
    private String paisHomologado;
}
