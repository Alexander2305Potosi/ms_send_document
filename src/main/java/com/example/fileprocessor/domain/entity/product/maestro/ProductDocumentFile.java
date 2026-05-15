package com.example.fileprocessor.domain.entity.product.maestro;

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
public class ProductDocumentFile {
    private String productId;
    private String documentId;
    private String filename;
    private byte[] content;
    private String contentType;
    private long size;
    private boolean isZip;
    private String origin;
    private String pais;
}