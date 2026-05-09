package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ProductDocumentResponse {
    @JsonProperty("documentId")
    private String documentId;
    @JsonProperty("filename")
    private String filename;
    @JsonProperty("content")
    private String content;
    @JsonProperty("contentType")
    private String contentType;
    @JsonProperty("size")
    private Long size;
    @JsonProperty("isZip")
    private boolean isZip;
    @JsonProperty("origin")
    private String origin;
    @JsonProperty("pais")
    private String pais;
}
