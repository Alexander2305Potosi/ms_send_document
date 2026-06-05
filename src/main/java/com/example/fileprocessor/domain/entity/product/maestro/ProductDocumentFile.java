package com.example.fileprocessor.domain.entity.product.maestro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonAlias;

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
    private Boolean isZip;

    @JsonAlias({"origin", "originFolder"})
    private String originFolder;

    @JsonAlias({"pais", "originCountry"})
    private String originCountry;
}