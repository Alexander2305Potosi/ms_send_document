package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductInfo {
    private final String productId;
    private final String name;
    private final List<ProductDocumentInfo> documents;
}
