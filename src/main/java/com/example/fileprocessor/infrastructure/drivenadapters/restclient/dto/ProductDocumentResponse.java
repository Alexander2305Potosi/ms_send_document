package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Typed response for a document from the products REST API.
 */
public record ProductDocumentResponse(
    @JsonProperty("documentId") String documentId,
    @JsonProperty("filename") String filename,
    @JsonProperty("content") String content,
    @JsonProperty("contentType") String contentType,
    @JsonProperty("size") Long size,
    @JsonProperty("isZip") Boolean isZip,
    @JsonProperty("origin") String origin,
    @JsonProperty("pais") String pais
) {}
