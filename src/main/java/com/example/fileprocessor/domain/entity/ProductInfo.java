package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Product information received from external REST API.
 * Contains product metadata and associated documents.
 */
@Getter
@Builder
public class ProductInfo {
    private final String productId;
    private final String name;
    private final List<ProductDocumentInfo> documents;
}
