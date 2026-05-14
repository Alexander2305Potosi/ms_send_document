package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    @JsonProperty("productId")
    private String productId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("documents")
    private List<ProductDocumentResponse> documents;
}
