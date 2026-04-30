package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Typed response from the products REST API.
 */
public record ProductResponse(
    @JsonProperty("productId") String productId,
    @JsonProperty("name") String name,
    @JsonProperty("documents") List<ProductDocumentResponse> documents
) {}
