package com.example.fileprocessor.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocumentHistory {
    private String productId;
    private boolean isZip;
    private String pais;
    private Long id;
    private String documentId;
    private Boolean active;
    private String docKey;
    private String name;
    private String owner;
    private String path;
    private String status;
    private String versionContract;
    private String state;
    private String errorMessage;
    private String filename;
    private String contentType;
    private Long size;
    private String origin;
    private byte[] content;
    private String parentZipName;

    public static ProductDocumentHistory from(ProductDocumentFile file) {
        return ProductDocumentHistory.builder()
            .documentId(file.getDocumentId())
            .filename(file.getFilename())
            .content(file.getContent())
            .contentType(file.getContentType())
            .size(file.getSize())
            .isZip(file.isZip())
            .origin(file.getOrigin())
            .pais(file.getPais())
            .build();
    }
}